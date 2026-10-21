#!/usr/bin/env python3
"""실험 5초 샘플러 (VM에서 실행). 앱 Prometheus 지표 + 호스트 CPU/메모리 + 실험 DB 문항 상태를 TSV로 쓴다.
사용: exp-sampler.py <out.tsv> <stop_file> <abort_file> <mysql_container> [interval=5]
출력 한 줄: epoch \t key \t value
중단 감시(30초마다): VM 여유 메모리 2GB 미만, 운영 사이트 응답 실패, 운영 앱 새 에러 로그 -> abort_file에 이유를 쓴다.
"""
import os, re, subprocess, sys, time, urllib.request

out, stop, abort, mysql = sys.argv[1:5]
interval = float(sys.argv[5]) if len(sys.argv) > 5 else 5.0
PROM = "http://127.0.0.1:18081/actuator/prometheus"
WANT = re.compile(r'^(opicnic_worker_queued|opicnic_worker_in_flight|opicnic_worker_circuit_open|opicnic_worker_items_total|'
                  r'opicnic_retry_total|opicnic_worker_failures_total|opicnic_external_call_seconds_count|'
                  r'hikaricp_connections_active|hikaricp_connections_pending|hikaricp_connections_max|process_cpu_usage|system_cpu_usage|'
                  r'jvm_gc_pause_seconds_count|jvm_gc_pause_seconds_sum|'
                  r'http_server_requests_seconds_count|http_server_requests_seconds_sum)(\{[^}]*\})? ([0-9.eE+-]+|NaN)$')
HEAP = re.compile(r'^jvm_memory_used_bytes\{[^}]*area="heap"[^}]*\} ([0-9.eE+-]+)$')
t_start = time.time()
last_guard = 0.0
prev_cpu = None

def host_cpu():
    global prev_cpu
    f = [float(x) for x in open('/proc/stat').readline().split()[1:]]
    idle = f[3] + f[4]; total = sum(f)
    cur = (idle, total)
    if prev_cpu is None:
        prev_cpu = cur; return None
    di, dt = cur[0] - prev_cpu[0], cur[1] - prev_cpu[1]
    prev_cpu = cur
    return 100.0 * (1 - di / dt) if dt else None

def mem_avail_mb():
    for l in open('/proc/meminfo'):
        if l.startswith('MemAvailable:'):
            return int(l.split()[1]) // 1024

def guard(now):
    reasons = []
    m = mem_avail_mb()
    if m < 2048: reasons.append(f"VM 여유 메모리 {m}MB < 2048MB")
    try:
        code = subprocess.run(['curl', '-s', '-o', '/dev/null', '-m', '10', '-w', '%{http_code}', 'https://opicnic.xyz/'],
                              capture_output=True, text=True).stdout.strip()
        if not code.startswith(('2', '3')): reasons.append(f"운영 사이트 응답 {code}")
    except Exception as e:
        reasons.append(f"운영 사이트 확인 실패 {e}")
    r = subprocess.run(['docker', 'logs', 'opicnic_app', '--since', str(int(t_start))], capture_output=True, text=True)
    n = sum(1 for l in (r.stdout + r.stderr).splitlines() if ' ERROR ' in l)
    if n: reasons.append(f"운영 앱 ERROR 로그 {n}줄")
    if reasons and not os.path.exists(abort):
        open(abort, 'w').write('; '.join(reasons) + '\n')

def db_counts():
    q = ("select coalesce(sum(status='QUEUED'),0), coalesce(sum(status='QUEUED' and next_attempt_at>now(6)),0),"
         "coalesce(sum(status='PROCESSING'),0), coalesce(sum(status='DONE'),0), coalesce(sum(status='FAILED'),0) from scoring_job_item")
    r = subprocess.run(['docker', 'exec', mysql, 'mysql', '-uroot', '-pexp-root-pass', 'opicnic', '-N', '-e', q],
                       capture_output=True, text=True)
    p = r.stdout.split()
    return dict(zip(['db_queued', 'db_queued_backoff', 'db_processing', 'db_done', 'db_failed'], p)) if len(p) == 5 else {}

with open(out, 'a') as f:
    while not os.path.exists(stop):
        t0 = time.time()
        rows = []
        try:
            txt = urllib.request.urlopen(PROM, timeout=4).read().decode()
            heap = 0.0
            for l in txt.splitlines():
                m = WANT.match(l)
                if m:
                    labels = m.group(2) or ''
                    if m.group(1).startswith('http_server_requests') and 'uri="/api/scoring-jobs"' not in labels: continue
                    rows.append((m.group(1) + labels, m.group(3)))
                    continue
                h = HEAP.match(l)
                if h: heap += float(h.group(1))
            rows.append(('heap_used_mb', f'{heap/1048576:.0f}'))
        except Exception as e:
            rows.append(('prom_error', str(e).replace('\t', ' ')[:80]))
        c = host_cpu()
        if c is not None: rows.append(('host_cpu_pct', f'{c:.1f}'))
        rows.append(('host_mem_avail_mb', str(mem_avail_mb())))
        for k, v in db_counts().items(): rows.append((k, v))
        now = time.time()
        for k, v in rows: f.write(f'{now:.1f}\t{k}\t{v}\n')
        f.flush()
        if now - last_guard >= 30:
            last_guard = now; guard(now)
        time.sleep(max(0, interval - (time.time() - t0)))
