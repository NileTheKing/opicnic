#!/usr/bin/env python3
"""재시도 폭풍 실행 1회 요약. 사용: exp-storm.py <run_dir> (sampler.tsv, marks.env)
2단계 시나리오(T_DOWN 전면 장애 -> T_UP 복구, 내 한도 있음)에서 복구 뒤를 본다.
  복구 후 429 비율 = 복구 후 0~300초 외부 호출 중 429 비율(종류별, 전체)
  초당 최대 호출 = 표본 간격(5초) 평균의 최대
  해소 = 대기 줄 0 도달 시각(실측) 또는 복구 후 120~끝 기울기로 0까지 선형 외삽"""
import json, sys, collections, statistics as st
D = sys.argv[1].rstrip('/')
m = dict(l.strip().split('=', 1) for l in open(f'{D}/marks.env') if '=' in l)
S = collections.defaultdict(list)
for l in open(f'{D}/sampler.tsv'):
    p = l.rstrip('\n').split('\t')
    if len(p) == 3:
        try: S[p[1]].append((float(p[0]), float(p[2])))
        except ValueError: pass
Td, Tu = float(m['T_DOWN']), float(m['T_UP'])
Q = S['opicnic_worker_queued']; tend = max(t for t, _ in Q)
def at(ser, t):
    b = [v for x, v in ser if x <= t]; return b[-1] if b else 0.0
def calls(t0, t1):
    c = collections.Counter()
    for k, ser in S.items():
        if not k.startswith('opicnic_external_call_seconds_count'): continue
        kind = k.split('kind="')[1].split('"')[0]; oc = k.split('outcome="')[1].split('"')[0]
        c[(kind, oc)] += at(ser, t1) - at(ser, t0)
    return c
w1 = min(Tu + 300, tend)
c = calls(Tu, w1)
tot = sum(c.values()); r429 = sum(v for (k, o), v in c.items() if o == '429')
by = {kind: {'calls': round(sum(v for (k, o), v in c.items() if k == kind)),
             '429_pct': round(100 * sum(v for (k, o), v in c.items() if k == kind and o == '429') / max(1, sum(v for (k, o), v in c.items() if k == kind)), 1)}
      for kind in sorted({k for k, _ in c})}
# 초당 최대 호출(복구 후, 5초 표본)
ts = sorted({t for k, ser in S.items() if k.startswith('opicnic_external_call_seconds_count') for t, _ in ser if Tu <= t <= w1})
def total_at(t): return sum(at(ser, t) for k, ser in S.items() if k.startswith('opicnic_external_call_seconds_count'))
peak = max(((total_at(b) - total_at(a)) / (b - a) for a, b in zip(ts, ts[1:]) if b > a), default=0)
q_up = at(Q, Tu)
zero = next((t for t, v in Q if t > Tu and v <= 0), None)
pts = [(t - Tu, v) for t, v in Q if Tu + 120 <= t <= tend]
ext = None
if len(pts) >= 5:
    mx = st.mean(x for x, _ in pts); my = st.mean(y for _, y in pts)
    sl = sum((x - mx) * (y - my) for x, y in pts) / sum((x - mx) ** 2 for x, _ in pts)
    if sl < 0: ext = round(mx + my / -sl)
R = {'run': m['RUN'], 'ladder': m.get('LADDER'), 'outage_s': round(Tu - Td), 'q_at_up': round(q_up),
     'after_up_window_s': round(w1 - Tu), 'calls_after_up': round(tot), '429_pct_after_up': round(100 * r429 / max(1, tot), 1),
     'by_kind': by, 'peak_calls_per_s': round(peak, 1),
     'calls_whole_run': round(sum(calls(Td, tend).values())),
     'calls_during_outage': round(sum(calls(Td, Tu).values())),
     'drain_s_actual': round(zero - Tu) if zero else None, 'drain_s_extrap': ext,
     'circuit_open_ratio_outage': round(st.mean(1 if v >= 1 else 0 for t, v in S['opicnic_worker_circuit_open'] if Td <= t <= Tu), 2),
     'circuit_open_ratio_after_up': round(st.mean(1 if v >= 1 else 0 for t, v in S['opicnic_worker_circuit_open'] if Tu <= t <= w1), 2),
     'aborted': m.get('RESULT') == 'abort'}
json.dump(R, open(f'{D}/storm.json', 'w'), ensure_ascii=False, indent=1)
print(json.dumps(R, ensure_ascii=False))
