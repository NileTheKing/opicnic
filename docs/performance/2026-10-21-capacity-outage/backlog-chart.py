# 3단계 본 결과 그림(포트폴리오용, 간결판): 대기 줄 시계열(칸 3, 6, 8). 사용: python3 backlog-chart.py <s3n-timeseries.csv> <out.svg>
import csv, sys
CSV, OUT = sys.argv[1], sys.argv[2]
W, H = 1600, 520
X0, X1, Y0, Y1 = 150, 1560, 70, 400            # 그림 영역(px)
XMIN, XMAX, YMAX = -5.3, 25.5, 13500           # 분(복구 기준), 문항
FONT = "Apple SD Gothic Neo, sans-serif"
INK, MUTED, GRID = "#222", "#6B7078", "#E6E7EA"
S = [  # 칸, 이름, 색, 정상 복귀(복구 기준 초, 외삽)
    (3, "기존", "#8A8F98", 1440),
    (6, "AIMD", "#2F6DB5", 1011),
    (8, "헤더 기반", "#1F8A5B", 671),
]
px = lambda m: X0 + (m - XMIN) / (XMAX - XMIN) * (X1 - X0)
py = lambda q: Y1 - q / YMAX * (Y1 - Y0)
def text(x, y, s, size=22, fill=INK, w=400, anchor="start"):
    return f'<text x="{x:.1f}" y="{y:.1f}" font-family="{FONT}" font-size="{size}" font-weight="{w}" fill="{fill}" text-anchor="{anchor}">{s}</text>'

data = {c: [] for c, *_ in S}
for r in csv.DictReader(open(CSV)):
    c, t = int(r["cell"]), int(r["t_from_drop_s"])
    if c in data and 0 <= t <= 660:            # 유입이 유지된 구간만 (660초 뒤는 유입 없는 잔여 처리)
        data[c].append(((t - 300) / 60, int(r["queued"])))

o = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">', f'<rect width="{W}" height="{H}" fill="#fff"/>']
o.append(f'<rect x="{px(-5):.1f}" y="{Y0-40}" width="{px(0)-px(-5):.1f}" height="{Y1-Y0+40}" fill="#FBECE8"/>')
o.append(text((px(-5)+px(0))/2, Y0-12, "한도 40%", 24, "#B23A2A", 700, "middle"))
for q, lab in [(5000, "5천"), (10000, "1만")]:
    o.append(f'<line x1="{X0}" y1="{py(q):.1f}" x2="{X1}" y2="{py(q):.1f}" stroke="{GRID}" stroke-width="1.5"/>')
    o.append(text(X0-14, py(q)+8, lab, 22, MUTED, 400, "end"))
o.append(text(X0-14, py(0)+8, "0", 22, MUTED, 400, "end"))
o.append(text(X0-14, Y0-12, "백로그", 22, MUTED, 400, "end"))
o.append(f'<line x1="{X0}" y1="{Y1}" x2="{X1}" y2="{Y1}" stroke="#BFC2C7" stroke-width="1.5"/>')
o.append(text(px(-5), Y1+34, "-5분", 22, MUTED, 400, "middle"))
o.append(text(px(0), Y1+34, "복구", 22, MUTED, 400, "middle"))
for c, name, col, drain in S:
    pts = " ".join(f"{px(x):.1f},{py(y):.1f}" for x, y in data[c])
    o.append(f'<polyline points="{pts}" fill="none" stroke="{col}" stroke-width="4" stroke-linejoin="round" stroke-linecap="round"/>')
    lx, ly = data[c][-1]
    o.append(f'<line x1="{px(lx):.1f}" y1="{py(ly):.1f}" x2="{px(drain/60):.1f}" y2="{py(0):.1f}" stroke="{col}" stroke-width="3" stroke-dasharray="9 7"/>')
    o.append(f'<circle cx="{px(drain/60):.1f}" cy="{py(0):.1f}" r="9" fill="{col}" stroke="#fff" stroke-width="3"/>')
    o.append(text(px(drain/60), Y1+40, f"{round(drain/60)}분", 30, col, 700, "middle"))
    o.append(text(px(drain/60), Y1+72, name, 22, INK, 400, "middle"))
# 실측/추정 (오른쪽 위)
lx = X1 - 330
o.append(f'<line x1="{lx}" y1="{Y0-20}" x2="{lx+40}" y2="{Y0-20}" stroke="{MUTED}" stroke-width="4"/>')
o.append(text(lx+50, Y0-12, "실측", 22, MUTED))
o.append(f'<line x1="{lx+130}" y1="{Y0-20}" x2="{lx+170}" y2="{Y0-20}" stroke="{MUTED}" stroke-width="3" stroke-dasharray="9 7"/>')
o.append(text(lx+180, Y0-12, "추세로 추정", 22, MUTED))
o.append("</svg>")
open(OUT, "w").write("\n".join(o))
