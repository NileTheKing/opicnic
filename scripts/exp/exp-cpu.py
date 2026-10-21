#!/usr/bin/env python3
"""호스트 전체 CPU를 interval초마다 window초 동안 재서 'mean max'(%)를 출력한다. 실험 시작 전 '조용한가' 확인용."""
import sys, time
window = float(sys.argv[1]); interval = float(sys.argv[2]) if len(sys.argv) > 2 else 5.0
def read():
    f = [float(x) for x in open('/proc/stat').readline().split()[1:]]
    return f[3] + f[4], sum(f)
samples = []; p = read(); end = time.time() + window
while time.time() < end:
    time.sleep(interval); c = read()
    di, dt = c[0] - p[0], c[1] - p[1]; p = c
    samples.append(100.0 * (1 - di / dt) if dt else 0.0)
print(f"{sum(samples)/len(samples):.1f} {max(samples):.1f}")
