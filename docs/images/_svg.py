# 블로그 그림 공통 도우미 — 토스식 단순 구조도 톤(연회색 배경, 둥근 박스, 강조색 하나)
FS = 1.25  # 글자 배율 — velog 본문 폭(약 1,040px)에서 1,200px 그림이 줄어들어 작은 글자가 안 읽혀서
FONT = "'Apple SD Gothic Neo','Pretendard','Noto Sans KR',sans-serif"
C = dict(bg='#F2F4F6', card='#FFFFFF', navy='#1E3A5F', line='#8B95A1', text='#333D4B', sub='#6B7684',
         blue='#3182F6', bluebg='#E8F3FF', red='#E5484D', redbg='#FDECEC', green='#12A36B', greenbg='#E6F6EF',
         border='#D1D6DB')

def svg(w, h, body):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" font-family="{FONT}">
<defs>
  <marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{C['line']}"/></marker>
  <marker id="arrb" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{C['blue']}"/></marker>
  <marker id="arrr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="{C['red']}"/></marker>
</defs>
<rect width="{w}" height="{h}" fill="white"/>
{body}
</svg>'''

def rect(x, y, w, h, fill, stroke=None, r=14, sw=1.5):
    s = f' stroke="{stroke}" stroke-width="{sw}"' if stroke else ''
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}"{s}/>'

def text(x, y, t, size=16, fill=None, weight=400, anchor='middle'):
    fill = fill or C['text']
    size = round(size * FS, 1)
    return f'<text x="{x}" y="{y}" font-size="{size}" font-weight="{weight}" fill="{fill}" text-anchor="{anchor}" dominant-baseline="middle">{t}</text>'

def box(x, y, w, h, label, kind='navy', size=16, sub=None):
    if kind == 'navy':
        out = rect(x, y, w, h, C['navy']) + text(x + w/2, y + h/2 - (9 if sub else 0), label, size, 'white', 600)
        if sub: out += text(x + w/2, y + h/2 + 13, sub, 12, '#C9D3E0')
    elif kind == 'white':
        out = rect(x, y, w, h, 'white', C['border']) + text(x + w/2, y + h/2 - (9 if sub else 0), label, size, C['text'], 600)
        if sub: out += text(x + w/2, y + h/2 + 13, sub, 12, C['sub'])
    return out

def pill(cx, cy, label, color='blue', size=13):
    w = 18 + len(label) * size * FS * 0.95
    fg, bg = C[color], C[color + 'bg']
    return rect(cx - w/2, cy - 16, w, 32, bg, fg, r=13, sw=1.2) + text(cx, cy + 1, label, size, fg, 700)

def arrow(x1, y1, x2, y2, color='line', dash=False, label=None, lx=None, ly=None, lsize=13, lanchor='middle', width=1.8):
    m = {'line': 'arr', 'blue': 'arrb', 'red': 'arrr'}[color]
    d = ' stroke-dasharray="6 5"' if dash else ''
    out = f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{C[color]}" stroke-width="{width}" marker-end="url(#{m})"{d}/>'
    if label:
        out += text(lx if lx is not None else (x1 + x2)/2, ly if ly is not None else (y1 + y2)/2 - 12, label, lsize,
                    C['sub'] if color == 'line' else C[color], 500, lanchor)
    return out

def path(d, color='line', dash=False):
    m = {'line': 'arr', 'blue': 'arrb', 'red': 'arrr'}[color]
    ds = ' stroke-dasharray="6 5"' if dash else ''
    return f'<path d="{d}" fill="none" stroke="{C[color]}" stroke-width="1.8" marker-end="url(#{m})"{ds}/>'

def cylinder(x, y, w, h, label, sub=None, size=16):
    # 저장소 기호: 윗면 타원 + 몸통 + 아랫면 호
    ry = 13
    edge = '#98A2AE'
    body = (f'<path d="M{x},{y+ry} L{x},{y+h-ry} A{w/2},{ry} 0 0 0 {x+w},{y+h-ry} L{x+w},{y+ry}" '
            f'fill="white" stroke="{edge}" stroke-width="1.8"/>')
    top = f'<ellipse cx="{x+w/2}" cy="{y+ry}" rx="{w/2}" ry="{ry}" fill="#E9EDF2" stroke="{edge}" stroke-width="1.8"/>'
    cy = y + ry + (h - ry) / 2
    out = body + top + text(x + w/2, cy - (9 if sub else 0), label, size, C['text'], 600)
    if sub: out += text(x + w/2, cy + 14, sub, 12, C['sub'])
    return out
