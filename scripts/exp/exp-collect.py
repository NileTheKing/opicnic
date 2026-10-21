#!/usr/bin/env python3
"""실행 1회 결과 요약 (VM 또는 로컬에서 실행). 입력: exp-run.sh가 만든 결과 폴더. 출력: summary.json + 사람이 읽는 텍스트.
사용: exp-collect.py <run_dir>
"""
import json, os, re, statistics as st, sys
from datetime import datetime

D = sys.argv[1]
marks = {}
for l in open(f'{D}/marks.env'):
    if '=' in l:
        k, v = l.rstrip('\n').split('=', 1); marks[k] = v
stage = int(marks['STAGE']); WARM = int(marks['WARM_S']); MEAS = int(marks['MEASURE_S'])

def parse_prom(p):
    d = {}
    if not os.path.exists(p): return d
    for l in open(p):
        if l.startswith('#') or ' ' not in l: continue
        k, v = l.rsplit(' ', 1)
        try: d[k] = float(v)
        except ValueError: pass
    return d

def pct(xs, q):
    xs = sorted(xs)
    if not xs: return None
    i = (len(xs) - 1) * q; lo = int(i); hi = min(lo + 1, len(xs) - 1)
    return xs[lo] + (xs[hi] - xs[lo]) * (i - lo)

def hist_quantile(a, b, q, uri='/api/scoring-jobs', status='202'):
    bk = {}
    for k, v in b.items():
        if k.startswith('http_server_requests_seconds_bucket') and f'uri="{uri}"' in k and f'status="{status}"' in k:
            le = re.search(r'le="([^"]+)"', k).group(1)
            bk[le] = bk.get(le, 0) + v - a.get(k, 0)
    pts = sorted(((float('inf') if le == '+Inf' else float(le)), c) for le, c in bk.items())
    if not pts or pts[-1][1] <= 0: return None
    target = q * pts[-1][1]; prev_le, prev_c = 0.0, 0.0
    for le, c in pts:
        if c >= target:
            if le == float('inf'): return prev_le
            return prev_le + (le - prev_le) * ((target - prev_c) / (c - prev_c) if c > prev_c else 1)
        prev_le, prev_c = le, c
    return None

def sumkey(d, prefix, must):
    return sum(v for k, v in d.items() if k.startswith(prefix) and all(m in k for m in must))

# ---- 샘플러
S = {}
if os.path.exists(f'{D}/sampler.tsv'):
    for l in open(f'{D}/sampler.tsv'):
        p = l.rstrip('\n').split('\t')
        if len(p) == 3:
            try: S.setdefault(p[1], []).append((float(p[0]), float(p[2])))
            except ValueError: pass

def at(key, t):
    xs = S.get(key, []); best = None
    for ts, v in xs:
        if ts <= t: best = v
        else: break
    if best is None and xs: best = xs[0][1]
    return best

def win(key, t1, t2):
    if key not in S:   # 라벨이 붙은 키(예: hikaricp_connections_active{pool="..."})는 접두사로 찾는다
        ks = [k for k in S if k.startswith(key + '{')]
        key = ks[0] if ks else key
    return [v for ts, v in S.get(key, []) if t1 <= ts <= t2]

def stats(xs):
    if not xs: return None
    return {'avg': round(st.mean(xs), 2), 'max': max(xs), 'min': min(xs)}

def ext_delta(t1, t2):
    out = {}
    for k in S:
        if k.startswith('opicnic_external_call_seconds_count'):
            kind = re.search(r'kind="([^"]+)"', k); oc = re.search(r'outcome="([^"]+)"', k)
            key = f"{kind.group(1) if kind else '?'}/{oc.group(1) if oc else '?'}"
            out[key] = out.get(key, 0) + (at(k, t2) or 0) - (at(k, t1) or 0)
    return {k: int(v) for k, v in sorted(out.items()) if v}

def counter_delta(prefix, must, t1, t2):
    return sum((at(k, t2) or 0) - (at(k, t1) or 0) for k in S if k.startswith(prefix) and all(m in k for m in must))

E = lambda n: float(marks[f'EPOCH_{n}'])
def dbt(n):
    s = marks[f'DBNOW_{n}'].replace('_', ' ')
    return datetime.strptime(s, '%Y-%m-%d %H:%M:%S.%f' if '.' in s else '%Y-%m-%d %H:%M:%S')

R = {'run': marks['RUN'], 'stage': stage, 'slots': int(marks['SLOTS']), 'circuit': marks['CIRCUIT'], 'ladder': marks.get('LADDER'), 'storage': marks.get('STORAGE')}

# ---- k6
try:
    k6 = json.load(open(f'{D}/k6-summary.json'))['metrics']
    def mv(n, f): return k6.get(n, {}).get('values', {}).get(f)
    R['k6'] = {
        'iter': mv('iter_measure', 'count'), 'sub202': mv('sub202_measure', 'count'), 'sub_other': mv('sub_other_measure', 'count') or 0,
        'fail': {c: mv(f'fail_{c}_measure', 'count') or 0 for c in ('csrf', 'start', 'urls', 'put')},
        'questions': mv('questions_measure', 'count'),
        'kinds': {c: mv(f'kind_{c}_measure', 'count') or 0 for c in 'CTM'},
        'submit_ms': {f: mv('submit_ms_measure', f) for f in ('avg', 'med', 'p(95)', 'p(99)', 'max')},
        'put_ms': {f: mv('put_ms_measure', f) for f in ('avg', 'p(95)', 'max')},
        'dropped_total': mv('dropped_iterations', 'count') or 0,
        'vus_max': mv('vus_max', 'value'),
    }
except Exception as e:
    R['k6'] = {'error': str(e)}

# ---- 창 정의
if stage == 1:
    t1, t2 = E('A'), E('B')
else:
    t1, t2 = E('A'), E('END')
R['window_s'] = round(t2 - t1)

pa, pb = parse_prom(f'{D}/prom_A.txt'), parse_prom(f'{D}/prom_B.txt' if stage == 1 else f'{D}/prom_END.txt')
cnt = sumkey(pb, 'http_server_requests_seconds_count', ['uri="/api/scoring-jobs"', 'status="202"']) - sumkey(pa, 'http_server_requests_seconds_count', ['uri="/api/scoring-jobs"', 'status="202"'])
sm = sumkey(pb, 'http_server_requests_seconds_sum', ['uri="/api/scoring-jobs"', 'status="202"']) - sumkey(pa, 'http_server_requests_seconds_sum', ['uri="/api/scoring-jobs"', 'status="202"'])
R['server_submit'] = {'count': int(cnt), 'avg_ms': round(sm / cnt * 1000, 1) if cnt else None,
                      'p95_ms': round((hist_quantile(pa, pb, 0.95) or 0) * 1000, 1) or None,
                      'p99_ms': round((hist_quantile(pa, pb, 0.99) or 0) * 1000, 1) or None}
non202 = sum(v for k, v in pb.items() if k.startswith('http_server_requests_seconds_count') and 'uri="/api/scoring-jobs"' in k and 'status="202"' not in k)
R['server_submit']['non202'] = int(non202)

# ---- 처리 속도/큐/자원 (측정 창)
done = counter_delta('opicnic_worker_items_total', ['outcome="done"'], t1, t2)
R['items_done'] = int(done); R['items_per_s'] = round(done / (t2 - t1), 2)
q = win('opicnic_worker_queued', t1, t2)
last60 = win('opicnic_worker_queued', t2 - 60, t2); first60 = win('opicnic_worker_queued', t1, t1 + 60)
R['queue'] = {'avg': round(st.mean(q), 1) if q else None, 'max': max(q) if q else None, 'end': q[-1] if q else None,
              'first60_avg': round(st.mean(first60), 1) if first60 else None, 'last60_avg': round(st.mean(last60), 1) if last60 else None}
R['in_flight'] = stats(win('opicnic_worker_in_flight', t1, t2))
R['heap_mb'] = stats(win('heap_used_mb', t1, t2))
R['hikari_active'] = stats(win('hikaricp_connections_active', t1, t2)); R['hikari_pending'] = stats(win('hikaricp_connections_pending', t1, t2))
R['proc_cpu'] = stats(win('process_cpu_usage', t1, t2)); R['host_cpu'] = stats(win('host_cpu_pct', t1, t2))
R['host_mem_avail_min_mb'] = min(win('host_mem_avail_mb', t1, t2) or [None]) if win('host_mem_avail_mb', t1, t2) else None
R['circuit_open_max'] = max(win('opicnic_worker_circuit_open', t1, t2) or [0])
R['external_calls_window'] = ext_delta(t1, t2)

# ---- 접수->완료 (DB)
jobs = []
if os.path.exists(f'{D}/jobs.tsv'):
    ja, jb = dbt('A'), dbt('B' if stage == 1 else 'END')
    def pdt(s):
        if not s or s == 'NULL': return None
        return datetime.strptime(s, '%Y-%m-%d %H:%M:%S.%f' if '.' in s else '%Y-%m-%d %H:%M:%S')
    for l in open(f'{D}/jobs.tsv'):
        p = l.rstrip('\n').split('\t')
        if len(p) < 6: continue
        c, f = pdt(p[3]), pdt(p[4])
        if c is None or not (ja <= c <= jb): continue
        cls = 'M' if p[1] == 'MOCK_EXAM' else ('T' if int(p[5]) == 1 else 'C')
        jobs.append({'cls': cls, 'status': p[2], 'dur': (f - c).total_seconds() if f else None, 'n': int(p[5]),
                     'c_epoch': E('A') + (c - ja).total_seconds()})
def jstat(js):
    d = [j['dur'] for j in js if j['dur'] is not None and j['status'] in ('COMPLETED', 'COMPLETED_WITH_FAILURES', 'FAILED')]
    return {'jobs': len(js), 'finished': len(d), 'unfinished': len(js) - len(d),
            'p50': round(pct(d, .5), 1) if d else None, 'p95': round(pct(d, .95), 1) if d else None,
            'max': round(max(d), 1) if d else None, 'avg': round(st.mean(d), 1) if d else None,
            'statuses': {s: sum(1 for j in js if j['status'] == s) for s in sorted({j['status'] for j in js})}}
R['jobs_all'] = jstat(jobs)
for c in 'CTM': R[f'jobs_{c}'] = jstat([j for j in jobs if j['cls'] == c])

# ---- 2단계, 3단계 공통(장애/용량 감소 -> 복구)
if stage in (2, 3):
    Td, Tu = float(marks['T_DOWN']), float(marks['T_UP']); Tdr = float(marks['T_DRAIN']); Te = float(marks['T_END'])
    R['outage'] = {'down_s': round(Tu - Td), 'drain_s': round(Tdr - Tu), 'drained': marks.get('DRAINED'),
                   'queue_at_up': marks.get('Q_AT_UP'), 'backoff_at_up(count,max_remaining_s)': marks.get('BACKOFF_AT_UP')}
    R['external_calls_pre_outage'] = ext_delta(t1, Td)
    R['external_calls_outage'] = ext_delta(Td, Tu)
    R['external_calls_recovery'] = ext_delta(Tu, Tdr)
    R['retries_outage'] = int(counter_delta('opicnic_retry_total', [], Td, Tu)); R['retries_recovery'] = int(counter_delta('opicnic_retry_total', [], Tu, Tdr))
    R['items_done_pre_outage_per_s'] = round(counter_delta('opicnic_worker_items_total', ['outcome="done"'], t1, Td) / (Td - t1), 2)
    R['items_done_recovery_per_s'] = round(counter_delta('opicnic_worker_items_total', ['outcome="done"'], Tu, Tdr) / max(1, Tdr - Tu), 2)
    qu = win('opicnic_worker_queued', Td, Tdr)
    R['queue_peak'] = max(qu) if qu else None
    R['circuit_open_samples_outage'] = sum(1 for v in win('opicnic_worker_circuit_open', Td, Tu) if v >= 1)
    R['circuit_open_samples_total'] = len([1 for v in win('opicnic_worker_circuit_open', Td, Tdr) if v >= 1])
    # 백오프 대기 몫: 복구 후 5초 표본마다 (QUEUED 중 next_attempt_at이 미래인 문항) / QUEUED
    share = []
    for (ts, bq), (_, tq) in zip(S.get('db_queued_backoff', []), S.get('db_queued', [])):
        if Tu <= ts <= Tdr and tq > 0: share.append((ts - Tu, bq, tq))
    R['backoff_share'] = {'samples': len(share),
                          'avg_share': round(st.mean([b / t for _, b, t in share]), 3) if share else None,
                          'series_every_30s': [(round(s), int(b), int(t)) for s, b, t in share if round(s) % 30 < 5][:60]}
    bo_zero = next((s for s, b, t in share if b == 0), None)
    R['backoff_share']['first_time_no_backoff_waiting_s'] = round(bo_zero) if bo_zero is not None else None
    sl = R['slots']; inf = [(ts - Tu, v) for ts, v in S.get('opicnic_worker_in_flight', []) if Tu <= ts <= Tdr]
    R['in_flight_after_up'] = {'avg': round(st.mean([v for _, v in inf]), 1) if inf else None,
                               'first_time_ge_90pct_slots_s': next((round(s) for s, v in inf if v >= 0.9 * sl), None)}

if stage == 3:
    R['jobs_created_during_cut'] = jstat([j for j in jobs if Td <= j['c_epoch'] < Tu])
    R['jobs_created_after_up'] = jstat([j for j in jobs if j['c_epoch'] >= Tu])
    th = float(marks['NORMAL_Q_THRESH'])
    ser = [(ts, v) for ts, v in S.get('opicnic_worker_queued', []) if ts >= Tu]
    ret = None
    for i in range(len(ser) - 2):
        if all(v <= th for _, v in ser[i:i + 3]): ret = round(ser[i][0] - Tu); break
    pre = win('opicnic_worker_queued', Td - 30, Td)
    peak_t = max(((v, ts) for ts, v in S.get('opicnic_worker_queued', []) if Td <= ts <= Tdr), default=None)
    ext_all = ext_delta(Td, Tdr)
    R['stage3'] = {'normal_q_threshold': th, 'base_q': marks.get('BASE_Q'), 'base_q_measured_avg': round(st.mean(pre), 1) if pre else None,
                   'return_to_normal_s': ret, 'returned': ret is not None,
                   'queue_peak': peak_t[0] if peak_t else None, 'queue_peak_at_s_from_cut': round(peak_t[1] - Td) if peak_t else None,
                   'calls_cut_to_normal': ext_all,
                   'r429_cut': sum(v for k, v in R['external_calls_outage'].items() if k.endswith('/429')),
                   'r429_recovery': sum(v for k, v in R['external_calls_recovery'].items() if k.endswith('/429')),
                   'r429_total': sum(v for k, v in ext_all.items() if k.endswith('/429')),
                   'waste_total': sum(v for k, v in ext_all.items() if not k.endswith('/ok')),
                   'ok_total': sum(v for k, v in ext_all.items() if k.endswith('/ok')),
                   'limiter_rate_min': {k.split('"')[1]: min(v for ts, v in S[k] if Td <= ts <= Tdr)
                                        for k in S if k.startswith('opicnic_outbound_limiter_rate') and any(Td <= ts <= Tdr for ts, _ in S[k])},
                   'heap_mb_max_all': max([v for _, v in S.get('heap_used_mb', [])] or [None]),
                   'host_mem_avail_min_all_mb': min([v for _, v in S.get('host_mem_avail_mb', [])] or [None])}

import gzip
try:
    lg = gzip.open(f'{D}/app.log.gz', 'rt', errors='replace').read().splitlines()
    R['app_log'] = {'ERROR_lines': sum(1 for l in lg if ' ERROR ' in l), 'deadlock_lines': sum(1 for l in lg if 'Deadlock found' in l and ' ERROR ' in l),
                    'WARN_lines': sum(1 for l in lg if ' WARN ' in l)}
except Exception as e:
    R['app_log'] = {'error': str(e)}
R['marks'] = {k: marks[k] for k in ('MEM_PRE_MB', 'MEM_POST_MB', 'CPU_PRE_MEAN', 'CPU_PRE_MAX', 'PROD_ERROR_LINES', 'LEFT_QUEUED', 'LEFT_INFLIGHT', 'RESULT') if k in marks}
json.dump(R, open(f'{D}/summary.json', 'w'), ensure_ascii=False, indent=1, default=str)
print(json.dumps(R, ensure_ascii=False, indent=1, default=str))
