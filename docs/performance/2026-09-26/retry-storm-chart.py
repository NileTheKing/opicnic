# 재시도 폭주 실험 그림 — retry-storm-*.csv(1초 샘플)를 초당 호출 누적 막대(성공/429/503)로, 복구 시점(0초)에 맞춰 세 줄로.
# 사용: python3 retry-storm-chart.py  → retry-storm.svg (PNG는 docs/local/blog-figures/_render.sh로)
import csv, os

HERE = os.path.dirname(os.path.abspath(__file__))
# (라벨, 제목, 장애 시작→복구 초: 스크립트 출력의 "장애 시작"과 "복구" 시각 차)
RUNS = [
    ("a-no-protection", "A. 보호 없음 (즉시 재시도, 동시 처리 1000)", 86),
    ("b-backoff-slots", "B. 지수 백오프 + 1초 jitter, 동시 처리 60", 77),
    ("c-full-jitter", "C. 지수 백오프 + full jitter, 동시 처리 60", 79),
]
SUMMARY = {
    "a-no-protection": "전체 호출 5,800건, 복구 후 429 78%, 복구→완료 59초",
    "b-backoff-slots": "전체 호출 873건, 복구 후 429 33%, 복구→완료 270초",
    "c-full-jitter": "전체 호출 766건, 복구 후 429 0.4%, 복구→완료 54초",
}
OK, R429, R5XX = "#2a78d6", "#eb6834", "#b9b8b0"
INK, INK2, GRID, SURF = "#1f1f1c", "#6b6a63", "#e6e5e0", "#fcfcfb"
W, PAD_L, PAD_R = 1100, 70, 30
PANEL_H, PANEL_GAP, TOP = 170, 70, 110
X_MIN, X_MAX, Y_MAX = -90, 290, 100
PW = W - PAD_L - PAD_R
H = TOP + len(RUNS) * (PANEL_H + PANEL_GAP) + 10
FONT = "-apple-system, 'Apple SD Gothic Neo', 'Noto Sans KR', sans-serif"

def x(t): return PAD_L + (t - X_MIN) / (X_MAX - X_MIN) * PW

out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" font-family="{FONT}">',
       f'<rect width="{W}" height="{H}" fill="{SURF}"/>',
       f'<text x="{PAD_L}" y="38" font-size="22" font-weight="700" fill="{INK}">외부 장애가 풀리는 순간의 초당 외부 호출</text>',
       f'<text x="{PAD_L}" y="64" font-size="15" fill="{INK2}">막대 = 초당 외부 호출 수. 콤보 30건 접수 후 60초 완전 장애, 0초에 복구(제공자 한도 초당 10건). 같은 부하, 서버 설정만 다름</text>']
lx = PAD_L
for color, name, w in ((OK, "성공", 70), (R429, "429 (한도 초과)", 150), (R5XX, "503 (장애 중)", 0)):
    out.append(f'<rect x="{lx}" y="80" width="14" height="14" rx="3" fill="{color}"/>'
               f'<text x="{lx + 20}" y="92" font-size="14" fill="{INK}">{name}</text>')
    lx += w

for i, (label, title, up_after) in enumerate(RUNS):
    top = TOP + i * (PANEL_H + PANEL_GAP) + 30
    base = top + PANEL_H
    y = lambda v: base - min(v, Y_MAX) / Y_MAX * PANEL_H
    out.append(f'<text x="{PAD_L}" y="{top - 14}" font-size="16" font-weight="700" fill="{INK}">{title}</text>')
    out.append(f'<text x="{W - PAD_R}" y="{top - 14}" font-size="14" fill="{INK2}" text-anchor="end">{SUMMARY[label]}</text>')
    # 장애 구간 음영, 격자, 한도선
    out.append(f'<rect x="{x(X_MIN)}" y="{top}" width="{x(0) - x(X_MIN)}" height="{PANEL_H}" fill="#f1f0ec"/>')
    out.append(f'<text x="{x(X_MIN) + 6}" y="{top + 18}" font-size="13" fill="{INK2}">장애</text>')
    for v in (0, 50, 100):
        out.append(f'<line x1="{PAD_L}" x2="{W - PAD_R}" y1="{y(v)}" y2="{y(v)}" stroke="{GRID}"/>'
                   f'<text x="{PAD_L - 8}" y="{y(v) + 4}" font-size="12" fill="{INK2}" text-anchor="end">{v}</text>')
    rows = list(csv.DictReader(open(os.path.join(HERE, f"retry-storm-{label}.csv"))))
    rec = 2 + up_after  # 샘플러가 장애 시작 2초 전에 켜짐
    for a, b in zip(rows, rows[1:]):
        t0, t1 = float(a["t"]) - rec, float(b["t"]) - rec
        if t1 < X_MIN or t0 > X_MAX: continue
        dt = max(t1 - t0, 0.5)
        d = {k: (int(b[k]) - int(a[k])) / dt for k in ("ok", "r429", "r5xx")}
        acc = 0
        for k, color in (("ok", OK), ("r429", R429), ("r5xx", R5XX)):
            if d[k] <= 0: continue
            h = y(acc) - y(acc + d[k])
            if h > 0.3:
                out.append(f'<rect x="{x(t0) + 0.4:.1f}" y="{y(acc + d[k]):.1f}" width="{max(x(t1) - x(t0) - 0.8, 0.8):.1f}" height="{h:.1f}" fill="{color}"/>')
            acc += d[k]
    out.append(f'<line x1="{PAD_L}" x2="{W - PAD_R}" y1="{y(10)}" y2="{y(10)}" stroke="{INK}" stroke-width="1.5" stroke-dasharray="5 4"/>'
               f'<text x="{W - PAD_R - 4}" y="{y(10) - 6}" font-size="12" fill="{INK}" text-anchor="end">한도 10/s</text>')
    out.append(f'<line x1="{x(0)}" x2="{x(0)}" y1="{top}" y2="{base}" stroke="{INK}" stroke-width="1.5"/>')
    out.append(f'<line x1="{PAD_L}" x2="{W - PAD_R}" y1="{base}" y2="{base}" stroke="{INK2}"/>')
    for t in range(-60, X_MAX + 1, 60):
        out.append(f'<text x="{x(t)}" y="{base + 18}" font-size="12" fill="{INK2}" text-anchor="middle">{t:+d}s</text>' if t else
                   f'<text x="{x(0)}" y="{base + 18}" font-size="12" font-weight="700" fill="{INK}" text-anchor="middle">복구</text>')
out.append("</svg>")
open(os.path.join(HERE, "retry-storm.svg"), "w").write("\n".join(out))
print("retry-storm.svg", W, H)
