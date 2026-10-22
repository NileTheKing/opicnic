#!/usr/bin/env python3
"""3단계 새 가정 실행 1회의 환산 지표. 사용: exp-s3n.py <run_dir> (exp-collect.py가 만든 summary.json과 sampler.tsv, marks.env가 있어야 함)
감소 구간(T_DOWN+30 ~ min(+120, 끝)) 의 대기 줄 기울기, 429 발생 속도로 5분(300초) 감소 상황을 환산하고,
회복 후 120~180초 기울기로 정상 복귀를 환산한다.
  쌓인 양(5분 환산) = 기울기 x 300
  정상 복귀(환산) = 120 + (쌓인 양 + 평소 대기 줄 + 회복 후 처음 120초 변화 측정값) / (-회복 후 120~180초 기울기)
원래 길이(감소 300초 이상) 실행이면 실측(5분 429, 회복 시점 대기 줄, 120~300초 기울기로 0까지 외삽한 복귀)도 같이 낸다."""
import json, sys, statistics as st, collections
D = sys.argv[1].rstrip('/')
marks = dict(l.strip().split('=', 1) for l in open(f'{D}/marks.env') if '=' in l)
S = collections.defaultdict(list)
for l in open(f'{D}/sampler.tsv'):
    p = l.rstrip('\n').split('\t')
    if len(p) == 3:
        try: S[p[1]].append((float(p[0]), float(p[2])))
        except ValueError: pass
Td, Tu = float(marks['T_DOWN']), float(marks['T_UP']); Lo = Tu - Td
base = float(marks.get('BASE_Q', 0))
def interp(ser, t):
    if not ser: return None
    if t <= ser[0][0]: return ser[0][1]
    for (a, va), (b, vb) in zip(ser, ser[1:]):
        if a <= t <= b: return va + (vb - va) * (t - a) / (b - a) if b > a else va
    return ser[-1][1]
def slope(ser, t0, t1):
    pts = [(t - t0, v) for t, v in ser if t0 <= t <= t1]
    if len(pts) < 5: return None, None, None
    mx = st.mean(x for x, _ in pts); my = st.mean(y for _, y in pts)
    sl = sum((x - mx) * (y - my) for x, y in pts) / sum((x - mx) ** 2 for x, _ in pts)
    return sl, my - sl * mx, pts   # 기울기, t0에서의 값(절편)
Q = S['opicnic_worker_queued']
def ext(t0, t1):
    out = collections.Counter()
    for k, ser in S.items():
        if not k.startswith('opicnic_external_call_seconds_count'): continue
        kind = k.split('kind="')[1].split('"')[0]; oc = k.split('outcome="')[1].split('"')[0]
        out[f'{kind}/{oc}'] += interp(ser, t1) - interp(ser, t0)
    return out
def agg(c):
    r429 = sum(v for k, v in c.items() if k.endswith('/429')); ok = {k: v for k, v in c.items() if k.endswith('/ok')}
    waste = sum(v for k, v in c.items() if not k.endswith('/ok'))
    return r429, waste, sum(v for k, v in ok.items() if k.startswith('stt/')), sum(v for k, v in ok.items() if not k.startswith('stt/'))
R = {'run': marks['RUN'], 'ladder': marks['LADDER'], 'count_rate_limited': marks.get('COUNT_RATE_LIMITED'), 'count_rejected': marks.get('COUNT_REJECTED'),
     'warm_s': marks['WARM_S'], 'outage_s': round(Lo), 'base_q': base}
# 감소 구간
w0, w1 = Td + 30, Td + min(120, Lo)
sl_o, _, _ = slope(Q, w0, w1)
r429, waste, stt_ok, llm_ok = agg(ext(w0, w1)); dur = w1 - w0
R['cut_window_s'] = [30, round(w1 - Td)]
R['queue_slope_cut_per_s'] = round(sl_o, 2)
R['pile_5min_extrap'] = round(sl_o * 300)
R['r429_rate_per_s'] = round(r429 / dur, 2); R['r429_5min_extrap'] = round(r429 / dur * 300)
R['waste_5min_extrap'] = round(waste / dur * 300)
R['stt_ok_per_s_cut'] = round(stt_ok / dur, 1); R['llm_ok_per_s_cut'] = round(llm_ok / dur, 1)   # llm = 채점 + 태깅 호출
oc = [v for t, v in S['opicnic_worker_circuit_open'] if Td <= t <= Tu]
R['circuit_open_ratio'] = round(sum(1 for v in oc if v >= 1) / len(oc), 3) if oc else None
R['q_at_up'] = round(interp(Q, Tu)); R['backoff_at_up'] = marks.get('BACKOFF_AT_UP')
# 회복 후
tail_end = max(t for t, _ in Q)
R['obs_after_up_s'] = round(tail_end - Tu)
d120 = interp(Q, Tu + 120) - interp(Q, Tu)
sl_p, _, _ = slope(Q, Tu + 120, Tu + 180)
R['post_up_q_change_first120'] = round(d120); R['post_slope_120_180_per_s'] = round(sl_p, 2)
r429r = agg(ext(Tu, Tu + 120))[0]; R['r429_first120_after_up'] = round(r429r)
R['return_extrap_s'] = round(120 + (sl_o * 300 + base + d120) / -sl_p) if sl_p < 0 else None
# 원래 길이 실측
if Lo >= 299:
    a429, awaste, _, _ = agg(ext(Td, Tu))
    R['actual'] = {'pile': round(interp(Q, Tu) - base), 'r429_5min': round(a429), 'waste_5min': round(awaste)}
    sl_f, q0, pts = slope(Q, Tu + 120, Tu + 300)
    if sl_f is not None and sl_f < 0:
        R['actual']['post_slope_120_300_per_s'] = round(sl_f, 2)
        R['actual']['return_s'] = round(300 + (q0 + sl_f * 180) / -sl_f)   # q0는 T_UP+120초 시점 값
    R['actual']['circuit_open_ratio_first120'] = None
    oc2 = [v for t, v in S['opicnic_worker_circuit_open'] if Td <= t <= Td + 120]
    R['actual']['circuit_open_ratio_first120'] = round(sum(1 for v in oc2 if v >= 1) / len(oc2), 3) if oc2 else None
    ex = R
    R['error_pct'] = {'pile': round((ex['pile_5min_extrap'] - R['actual']['pile']) / R['actual']['pile'] * 100, 1),
                      'r429': round((ex['r429_5min_extrap'] - R['actual']['r429_5min']) / max(1, R['actual']['r429_5min']) * 100, 1)}
    if 'return_s' in R['actual']: R['error_pct']['return'] = round((ex['return_extrap_s'] - R['actual']['return_s']) / R['actual']['return_s'] * 100, 1)
try:
    sm = json.load(open(f'{D}/summary.json'))
    R['server_submit_p95_ms'] = sm['server_submit']['p95_ms']; R['heap_mb_max'] = sm['heap_mb']['max']
    R['app_error_lines'] = sm['app_log'].get('ERROR_lines'); R['deadlock_lines'] = sm['app_log'].get('deadlock_lines')
    R['prod_error_lines'] = sm['marks'].get('PROD_ERROR_LINES'); R['host_mem_avail_min_mb'] = sm['stage3']['host_mem_avail_min_all_mb']
    R['calls_cut_total'] = {k: round(v) for k, v in ext(Td, Tu).items() if not k.endswith('/ok') or True}
except Exception as e:
    R['summary_error'] = str(e)
json.dump(R, open(f'{D}/s3n.json', 'w'), ensure_ascii=False, indent=1)
print(json.dumps(R, ensure_ascii=False, indent=1))
