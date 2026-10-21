#!/usr/bin/env python3
"""summary.json들을 README용 마크다운 표로 바꾼다. 사용: exp-table.py <1|2> <run_dir>... (VM에서 실행)"""
import json, sys
stage = sys.argv[1]; runs = [json.load(open(f'{d}/summary.json')) for d in sys.argv[2:]]
f = lambda x, d=1: '-' if x is None else (f'{x:.{d}f}' if isinstance(x, float) else str(x))
if stage == '1':
    print('| 회차 | 슬롯 | 202/전체 | 접수 서버 평균(ms) | 서버 p95(ms) | 접수 클라 p95(ms) | 처리(문항/s) | 큐 평균/최대/끝 | 큐 첫60s→끝60s평균 | in_flight 평균/최대 | 접수→완료 p50/p95/max(s) | 콤보 p95 | 유형 p95 | 모의 p95 | 미완료 | 힙 최대(MB) | Hikari 활성 최대/대기 최대 | 앱 CPU 평균 | ERROR 로그 |')
    print('|' + '---|' * 19)
    for r in runs:
        k = r['k6']; s = r['server_submit']; q = r['queue']; j = r['jobs_all']
        print(f"| {r['run']} | {r['slots']} | {k['sub202']}/{k['iter']} | {f(s['avg_ms'])} | {f(s['p95_ms'])} | {f(k['submit_ms']['p(95)'])} | {f(r['items_per_s'],2)} | "
              f"{f(q['avg'])}/{f(q['max'],0)}/{f(q['end'],0)} | {f(q['first60_avg'])}→{f(q['last60_avg'])} | {f(r['in_flight']['avg'],0)}/{f(r['in_flight']['max'],0)} | "
              f"{f(j['p50'])}/{f(j['p95'])}/{f(j['max'])} | {f(r['jobs_C']['p95'])} | {f(r['jobs_T']['p95'])} | {f(r['jobs_M']['p95'])} | {j['unfinished']} | "
              f"{f(r['heap_mb']['max'],0)} | {f(r['hikari_active']['max'],0)}/{f(r['hikari_pending']['max'],0)} | {f((r['proc_cpu'] or {}).get('avg'),2)} | {r['app_log']['ERROR_lines']} |")
elif stage == '3':
    print('| 회차 | 429 (감소 중/회복 후) | 헛호출 합(5xx·타임아웃·429) | 성공 호출 | 재시도 수(감소/회복) | 대기 줄 최대 (감소 후 초) | 정상 복귀(회복 후 s) | 접수→완료 p95 (전체/감소 중 접수) | 서버 접수 p95(ms) | 접수 202/전체 | 힙 최대(MB) | 호스트 여유 메모리 최소(MB) | 앱 ERROR 로그 (교착) | 운영 ERROR |')
    print('|' + '---|' * 14)
    for r in runs:
        o = r['stage3']; k = r['k6']
        ret = o['return_to_normal_s'] if o['returned'] else '미도달(' + str(r['outage']['drain_s']) + 's)'
        print(f"| {r['run']} | {o['r429_total']} ({o['r429_cut']}/{o['r429_recovery']}) | {o['waste_total']} | {o['ok_total']} | {r['retries_outage']}/{r['retries_recovery']} | "
              f"{f(o['queue_peak'],0)} ({o['queue_peak_at_s_from_cut']}) | {ret} | {f(r['jobs_all']['p95'])}/{f(r['jobs_created_during_cut']['p95'])} | {f(r['server_submit']['p95_ms'])} | "
              f"{k['sub202']}/{k['iter']} | {f(o['heap_mb_max_all'],0)} | {f(o['host_mem_avail_min_all_mb'],0)} | {r['app_log']['ERROR_lines']} ({r['app_log']['deadlock_lines']}) | {r['marks'].get('PROD_ERROR_LINES')} |")
else:
    print('| 회차 | 서킷 | 장애 중 헛호출(5xx+타임아웃) | 장애 중 성공 | 재시도 수(장애/복구) | 복구 후 헛호출 | 큐 최대 | 복구 시 큐 | 복구 후 대기 줄 0까지(s) | 복구 후 처리(문항/s) | 백오프 대기 몫(평균) | 백오프 대기 0까지(s) | 서킷 열림 표본(5s) | 접수 서버 p95(ms) | 접수 202/전체 | 접수→완료 p95(s) | 미완료 | ERROR 로그 |')
    print('|' + '---|' * 18)
    def waste(d): return sum(v for k, v in d.items() if not k.endswith('/ok'))
    def okc(d): return sum(v for k, v in d.items() if k.endswith('/ok'))
    for r in runs:
        o = r['outage']; k = r['k6']
        print(f"| {r['run']} | {r['circuit']} | {waste(r['external_calls_outage'])} | {okc(r['external_calls_outage'])} | {r['retries_outage']}/{r['retries_recovery']} | {waste(r['external_calls_recovery'])} | "
              f"{f(r['queue_peak'],0)} | {o['queue_at_up']} | {o['drain_s'] if o['drained']=='1' else '미도달('+str(o['drain_s'])+'s 후 큐 '+str(r['marks'].get('LEFT_QUEUED','?'))+')'} | {f(r['items_done_recovery_per_s'],2)} | "
              f"{f(r['backoff_share']['avg_share'],3)} | {f(r['backoff_share']['first_time_no_backoff_waiting_s'],0)} | {r['circuit_open_samples_total']} | {f(r['server_submit']['p95_ms'])} | {k['sub202']}/{k['iter']} | {f(r['jobs_all']['p95'])} | {r['jobs_all']['unfinished']} | {r['app_log']['ERROR_lines']} |")
