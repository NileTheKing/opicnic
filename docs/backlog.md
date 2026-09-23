# Backlog

**열린 작업만 둔다.** 끝난 건 `CHANGELOG.md`에 한 줄 적고 여기서 지운다 — Done 목록을 여기 쌓지 않는다(2026-09-18 전까지 그렇게 쌓여서 357줄이 됐다). 결정의 근거는 `adr/`, 검증 기준은 `performance/slo.md`.

## 지금 — 비동기 채점 후속 (ADR-0001 9절 "남은 것")

전환 자체는 끝났다(2026-09-22 운영 배포, 이력은 `CHANGELOG.md`). 여기는 그 뒤에 남은 것. VM 재측정(2026-09-22)으로 랩탑 숫자는 확정: 커넥션 풀·R2 읽기 문제는 랩탑 환경 탓, 콤보 SLO 통과. 순서: 서킷(다운 시나리오) → 작은 것들 → 블로그.

- [ ] **버그: 실제 LLM 429·5xx를 못 알아본다** (2026-09-23 발견, 미수정) — `ExternalCallMetrics.outcomeOf`가 `NonTransientAiException` 메시지가 "429"로 **시작**하는지 본다. 그런데 Spring AI 1.1.5 자동 설정(`SpringAiRetryAutoConfiguration`)의 오류 처리기는 "HTTP 429 - {...}" 형식으로 만든다(과거 운영 로그 4건 확인). 테스트(`ExternalCallMetricsTest`, `FeedbackServiceRateLimitDetectionTest`)는 상상한 형식 "429 - ..."을 넣어 통과. 영향: 채점·태깅의 실제 429가 일반 오류로 분류돼 짧은 백오프(2s부터, 429는 4s부터여야), Grafana 429 비율·재시도 사유에 안 잡힘, "HTTP 503"도 5xx로 안 잡힘. mock은 `HttpClientErrorException`을 던져 이 경로를 안 탐. 수정: ① `spring.ai.retry.on-http-codes: [429]`로 Spring AI가 429를 TransientAiException으로 주게(자체 재시도는 max-attempts 1이라 동작 불변) ② 메시지에서 상태 코드 세 자리를 `(?:HTTP )?(\d{3}) - ` 식으로 뽑아 판별 ③ 테스트에 실제 운영 메시지 그대로. 영구 실패 분류(`ScoringWorker.classify`)에도 LLM 400/413/422를 넣을지 같이 검토
- [ ] (스케일아웃할 때) 앱 서버 2대 이상 체크리스트 — ① 기동 시 PROCESSING 즉시 회수(`ScoringWorker.recoverOrphans`) 제거 또는 워커 id + 하트비트: 배포로 재시작한 서버가 살아 있는 다른 서버의 처리 중 문항을 되돌려 중복 처리·결과 중복 저장 ② 제출 전 상태(Caffeine attempt)와 로그인 세션 → Redis 또는 스티키 세션: 문제 받은 서버와 업로드 URL·접수 요청 서버가 다르면 410 ③ 서킷은 서버별 메모리(치명적 아님). 제출 후(폴링·결과·워커 집기)는 DB만 보므로 그대로. 먼저 돌릴 다이얼은 슬롯 수
- [ ] 슬롯 60 → 90~120 검토 — VM 100명에서 큐 max 106, 완료 p95 26.5s. 문항 시간이 안 늘어나므로 올리면 그대로 줄어듦. 모의고사 피크(450문항) 1분 SLO엔 90 필요(계산). 필요해질 때
- [ ] **워커 서킷 — 오작동 아님, 튜닝은 보류** — S2(호출당 45% 실패)에서 실행당 4~5회 열렸지만 (2026-09-23 정정: 오작동 아님. 시도 1회 = STT+채점 두 호출이라 호출당 45% 실패면 시도 실패율 ≈ 1-0.55² ≈ 70%, 실측 189/294 = 64%. 임계 80%를 가끔 넘는 게 설계대로다). S2는 서킷 입장에서 이미 "심한 장애"다. 값을 바꿀 근거는 없고, 재시도 정책(횟수 → 시간 예산) 결정 뒤 같이 본다 실패 주입률은 이제 실행 중에 바꿀 수 있다(dev `POST /api/practice-attempts/mock-failures?rate429=&rate5xx=&rateTimeout=`) — "제공자 다운"(1.0) 시나리오가 가능해졌다
- [ ] 폴링 초반 간격 — 무응답처럼 1~2초 만에 끝나는 잡은 서버 1.7s인데 화면 5s(운영 실사용). 첫 2~3회 0.5s 후 2s, 또는 SSE
- [ ] 처리 후 오디오 `pending/`→`attempts/` 복사(30일 보관, ADR 3절). 지금은 1일 후 삭제. 재청취 기능과 함께
- [ ] (우선순위 낮춤) 실패 문항 재채점 API `POST /api/scoring-jobs/{id}/items/{index}/retries` — 2026-09-23 재시도 시간 예산(30분) 이후 FAILED는 녹음 파일 문제·LLM 형식 오류 3회·30분 넘는 장애뿐이라 드물다. 필요해지면 음성 보관(1일)과 함께
- [ ] 블로그 — 초안 `local/2026-09-23-blog-draft-async-boundary.md`("처리량이 아니라 유실이었다", 경어체). 남은 것: 재시도 시간 예산·끝난 문항부터 보여주기 반영, 그림 3개, 발행처
- [ ] (아이디어, 착수 전) 장애 알림에 AI 판단 보조 — Alertmanager webhook → 정형 수집(Prometheus 5분치·워커 큐·서킷) + 결정론적 확인(Groq /v1/models에 우리 모델 생존) + 런북 → LLM이 가설·근거·추천 액션 1개 → Slack 카드에 버튼 → 사람 승인 후 미리 만든 액션만 실행(워커 일시정지 등). 원칙은 `user_dev_direction` 그대로(코드가 한도·멱등·권한 소유, AI는 근거 수집·판단 보조). n8n 불필요(단계 6개 전부 HTTP). 견적 2.5일: 런북+워커 일시정지 스위치 / webhook+정형수집+Slack 카드 / LLM 요약 / 승인 버튼+감사 로그 / mock 주입으로 시나리오 3개 검증. 선행 필요: 런북(`deployment.md` 몇 줄뿐), 모델 fallback 런타임 스위치 없음

## 운영 — 외부 LLM 제공자

- [ ] **모델 소멸 감지 장치가 없다.** 7/31, 8/31 두 번 다 우연히 발견. 응답 시간·힙·에러율 전부 정상이고 사용자만 실패 카드를 본다. 기동 시 모델 존재 확인이나 채점 실패율 알림 중 하나는 필요 (`ScoringFailureRateHigh` 알림이 9/18 생겼으니 그걸로 잡히는지 확인)
- [x] 워커 동시 상한 산정 스윕 — 30명엔 60이면 큐 0, 100명은 HikariCP 고갈로 접수 p95 8.5s. 기본값 60으로 (`performance/2026-09-22/slots-sweep.md`) — 2026-09-22
- [ ] (별건) axon 프로젝트 `axon-grafana`가 `0.0.0.0:3000`, `axon-mysql`이 `0.0.0.0:3306` 바인딩. 7/15 랜섬웨어와 같은 설정. 둘 다 `127.0.0.1:`로 — 그쪽에 전달

## 감사 후속 (archive/audit-followup-spec-2026-08-20.md 잔여)

- [ ] FU-05 / ADMIN-02: 모든 연습 진입점에서 pattern 조립 가능한 topic만 선택

## 제품 — 작은 것

- [ ] `/analytics/history` 페이지네이션 (최근 20개만, UI 없음)
- [ ] `/today` 회피 감지에 완전 미연습 유형도 포함할지 (현재는 1회 이상 연습한 유형만)
- [ ] `QuestionSet`이 `TYPE_1~10`을 다 갖는지 관리자 저장 시점 또는 테스트에서 검증
- [ ] 잘못된 topic/difficulty URL 파라미터 예외 처리
- [ ] 기존 `Combo` 엔티티/전략 계열 제거 여부 — 출제 source of truth가 아님(`PROJECT.md`)

## 제품 — 구상 단계 (착수 안 함)

### 집중 연습 모드 (`PracticeFocusController` 자리만 있음)
전체 5항목 대신 하나에 집중. 등급 관점: IL→IM은 mainPoint+content, IM→IH는 expression.
1. 메인포인트 집중 — 프롬프트는 mainPoint+content만, 결과도 그 둘 + 코칭 문장만
2. 어휘/표현 집중 — expression만, "이 문장을 더 풍부하게" 제안 포함
3. 문법/유창성 — 별도 모드 효과 의문, 보류
진입점: 홈 "집중 연습" 섹션, 학습분석 약점 항목 옆 버튼.

### React 전환 시
- `POST /api/practice-attempts` 시작 API 연결, `HttpServletRequest#getParts` 직접 파싱 제거, 결과 누적용 session 제거, `resultId` 기반 결과 조회, IndexedDB 녹음 Blob 임시 저장 — 비동기 전환과 겹치는 부분이 많아 ADR-0001 구현 후 다시 본다
