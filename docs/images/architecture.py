# README 아키텍처 그림. 사용: cd docs/images && python3 architecture.py → architecture.svg, PNG는 headless Chrome(2배)
from _svg import *
W, H = 1440, 740
b = [rect(0, 0, W, H, C['bg'], r=24)]

def card(x, y, w, h, title, top=False):
    # 제목은 화살표가 안 지나가는 쪽에 — 워커·Groq는 위로 화살표가 들어와 아래, 요청 처리는 아래로 선이 나가 위
    ty = y + 24 if top else y + h - 20
    return rect(x, y, w, h, C['card'], C['border'], r=18) + text(x + 18, ty, title, 13, C['sub'], 700, 'start')

AX, WX, GX = 235, 870, 1210        # 요청 처리 카드, 워커 카드, Groq 카드의 x
DB = 580                            # 저장소 x
b += [card(AX, 185, 250, 360, 'Spring Boot: 요청 처리', top=True),
      card(WX, 205, 250, 300, 'Spring Boot: 채점 워커'),
      card(GX, 205, 200, 300, 'Groq')]
b += [box(30, 300, 160, 72, '브라우저'),
      box(AX + 25, 235, 200, 72, '접수 API', sub='202 즉시 응답'),
      box(AX + 25, 335, 200, 72, '결과 조회', sub='2초 폴링'),
      box(AX + 25, 435, 200, 72, '코칭 리포트', sub='태그 집계 후 LLM')]
b += [rect(WX + 25, 235, 200, 110, C['navy']), text(WX + 125, 265, '워커', 16, 'white', 600),
      text(WX + 125, 297, '가상 스레드 동시 60', 12, '#C9D3E0'), text(WX + 125, 321, 'full jitter, 30분 재시도', 12, '#C9D3E0'),
      box(WX + 25, 370, 200, 64, '장애 대응', kind='white', size=14, sub='실패 분류, 서킷')]
b += [box(GX + 17, 235, 166, 64, 'Whisper', kind='white', sub='STT'),
      box(GX + 17, 330, 166, 80, 'gpt-oss', kind='white', sub='120b 채점 / 20b 태깅')]
b += [cylinder(DB, 55, 190, 100, 'S3', sub='녹음 (Cloudflare R2)'),
      cylinder(DB, 290, 190, 110, 'MySQL', sub='채점 작업 큐, 결과')]

# 흐름 ①~⑧
b += [path(f'M110,300 L110,105 L{DB-2},105', 'blue'), text(330, 88, '① 직접 업로드 (presigned URL)', 13, C['blue'], 600),
      arrow(190, 318, AX + 23, 280, label='② 제출', lx=208, ly=276),
      arrow(AX + 225, 275, DB - 2, 318, label='③ 기록', lx=(AX + 225 + DB) / 2, ly=276),
      arrow(DB + 190, 322, WX + 23, 285, label='④ 문항 집기', lx=(DB + 190 + WX) / 2, ly=276),
      arrow(DB + 150, 158, WX + 60, 233, 'blue', label='⑤ 음성 읽기', lx=WX - 10, ly=172, lanchor='start'),
      arrow(WX + 225, 267, GX + 15, 267, label='⑥ 호출', lx=(WX + 225 + GX) / 2, ly=248),
      arrow(WX + 23, 330, DB + 192, 362, label='⑦ 결과 저장', lx=(DB + 190 + WX) / 2, ly=380),
      path(f'M110,372 L110,390 L{AX + 23},390'), text(150, 408, '⑧ 폴링', 13, C['sub'], 500),
      arrow(AX + 225, 380, DB - 2, 372, dash=True),
      path(f'M{AX + 125},507 L{AX + 125},620 L{GX + 100},620 L{GX + 100},412'),
      text((AX + GX) / 2 + 60, 605, '코칭 리포트 작성 (집계 요약만 전달)', 13, C['sub'], 500)]

# 모니터링
b += [card(AX, 645, 1175, 80, '모니터링'),
      box(470, 657, 190, 56, 'Prometheus', kind='white', size=14, sub='앱, 워커 지표'),
      box(730, 657, 190, 56, 'Alertmanager', kind='white', size=14, sub='증상 기반 알림 5개'),
      box(990, 657, 140, 56, 'Discord', kind='white', size=14),
      box(1210, 657, 170, 56, 'Grafana', kind='white', size=14, sub='대시보드'),
      arrow(660, 685, 728, 685), arrow(920, 685, 988, 685)]
open('architecture.svg', 'w').write(svg(W, H, '\n'.join(b)))
print('architecture.svg')
