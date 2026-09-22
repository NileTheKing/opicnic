# Backlog

**열린 작업만 둔다.** 끝난 건 `CHANGELOG.md`에 한 줄 적고 여기서 지운다 — Done 목록을 여기 쌓지 않는다(2026-09-18 전까지 그렇게 쌓여서 357줄이 됐다). 결정의 근거는 `adr/`, 검증 기준은 `performance/slo.md`.

## 지금 — R2 + 비동기 전환 (ADR-0001)

순서대로. "전" 측정은 SLO 판정이 아니라 현상 기록이다(`slo.md` 전/후 표 참고).

- [x] S1 스크립트 + 전환 전 S1 — `scripts/archive/s1.sh`(동기 경로 제거로 보관), 7.6s (`performance/2026-09-18/s1-before.txt`)
- [x] 전환 전 S2·S3 — 증폭 1.50배(SLO 1.65), 동시 30에서 p95 7.56s로 1명과 동일 (`performance/2026-09-18/`). 동기의 문제는 속도가 아니라 유실·실패 시 사용자 재제출로 확정
- [x] 1단계: 잡 테이블 `ScoringJob`/`ScoringJobItem` + `AudioStorage`(R2/인메모리) — 2026-09-18
- [x] 2단계: 접수 API 3개 (`upload-urls` / `POST /api/scoring-jobs` 202 / `GET` 폴링) — 2026-09-19
- [x] 3단계: `ScoringWorker` — 이탈·재시작(접수 직후/처리 중) 완료율 100% 확인 (`scripts/s1-async.sh`) — 2026-09-19
- [x] 4단계: 화면 — question.html 모의고사 분기(R2 직접 업로드 → 접수), `progress.html` 폴링, 결과는 기존 feedback.html에 DB로. 브라우저로 전 구간 확인 — 2026-09-21
- [x] 콤보 이전(ADR 2단계) 1/2: 콤보·유형별 진입점 asyncScoring, 모드 제한 해제, 콤보 패턴/카테고리를 잡 행에 복사해 결과 행에 붙임(학습분석 사이클 유지), 결과 URL `/practice/result/{id}` — curl로 콤보 2문항 완주 확인 — 2026-09-21
- [x] Grafana 워커 행 + 지표 3개 추가(`opicnic_worker_queued`, `opicnic_job_duration_seconds`, 워커 `opicnic_retry_total`) — 2026-09-21
- [x] 콤보 이전 2/2: 옛 동기 경로 제거 — `/answers`·`retry`·`finalize`·세션 결과 화면·멀티파트 예외 매핑·`getComboFeedbackStreaming`·`saveFeedbackResults`, question.html 동기 분기. 채점 규칙 테스트는 `gradeWithSpeech`로 재타깃. 측정 스크립트 `scripts/archive/` — 2026-09-21
- [ ] **R2 + 비동기 구현 (나머지)** — [`adr/0001-async-r2.md`](adr/0001-async-r2.md) 4절. 대화에서 정한 구현 결정: dev 테스터 회원(nullable 대신) / 워커 동시 상한 설정값 30 / 숏폴링 2초, SSE는 후속 / submit 시 R2 HEAD 안 함 / URL 발급은 녹음 후(서명에 크기 포함) / **잡 행은 submit에서 생성**(화면 열 때 아님 — 약속 전엔 Caffeine) / 경로 `/api/scoring-jobs`. 남은 순서: 전환 후 S2~S4 → 운영 배포(VM `.env`에 R2 키) → ADR 결과 절·블로그.
  - 후속(1단계 범위 밖): 처리 후 오디오 `pending/`→`attempts/` 복사(30일 보관, ADR 3절). 지금은 pending/ 라이프사이클 1일로 지워짐
  - 후속: `COMPLETED_WITH_FAILURES` 문항의 사용자 재시도 API (`POST /api/scoring-jobs/{id}/items/{index}/retries`) presigned URL(`content-length-range` 4MB, 소유자·문항 범위, 10분 만료, 키는 서버가 `pending/{attemptId}/q{n}.webm`) → submit 시 R2 내부 복사로 `attempts/` → 잡 테이블(DB) + 가상 스레드 워커 폴링 → 문항별 즉시 저장 + 상태값 → finalize 제거 → 재시도 3회 상한 + FAILED 확정 + 워커 실패율 서킷.
  - 이때 같이: dev attempt(memberId=null)도 DB에 저장되게 — 안 그러면 S1의 kill/restart → DB 검증이 성립 안 함
  - VM `.env`에 R2 키 4개 넣기 (로컬 `.env`에만 있음)
- [x] 전환 후 S1(kill·restart 포함) → 표 S1 행 완성. 단계별 비용 실측(`2026-09-21/s1-async-timing.txt`) — 2026-09-21
- [x] 전환 후 S2 — `scripts/s2-async.sh`, 증폭 1.54/1.45(SLO 1.65 ✓), 접수 100% 202 — 2026-09-22
- [ ] **워커 서킷 튜닝** — S2(호출당 45% 실패)에서 서킷이 실행당 4~5회 열려 총 2~2.5분 정지, 접수→완료 avg 97~156s. 창 20·비율 0.8은 "제공자 다운" 감지용인데 부분 장애에서 오작동. 후보: 창 확대(50~100) / 429는 실패로 안 세기(백오프가 이미 처리) / 열림 30s→짧게+half-open. 결정 전에 S2를 서킷 off로 한 번 더 돌려 순수 처리 시간 확보
- [x] 전환 후 S3 — `scripts/s3-async.sh`(30명 5분 연속 제출), 접수 p95 0.032s, 완료 p95 18.3s, Full GC 0 — 2026-09-22
- [ ] 전환 후 S4(VM, 500VU) → 표 완성. 운영 배포 후
- [ ] 워커: R2 NoSuchKey(404)는 재시도 무의미 — 즉시 FAILED로. 지금은 3회 백오프 낭비(S3에서 1건)
- [ ] 블로그 초안 — "빠른 것과 안전한 것은 다르다"(S1 7.6s인데 완료율 0%)

## 운영 — 외부 LLM 제공자

- [ ] **모델 소멸 감지 장치가 없다.** 7/31, 8/31 두 번 다 우연히 발견. 응답 시간·힙·에러율 전부 정상이고 사용자만 실패 카드를 본다. 기동 시 모델 존재 확인이나 채점 실패율 알림 중 하나는 필요 (`ScoringFailureRateHigh` 알림이 9/18 생겼으니 그걸로 잡히는지 확인)
- [ ] 워커 동시 상한(`opicnic.worker.concurrency`, 지금 30) 산정 실험 — S3에서 30명 연속 제출 시 처리 중이 상한에 붙고 큐 max 40, 완료 p95가 단독 대비 +8s. 상한을 올리면 완료는 빨라지고 Groq 429는 늘어남(제공자 한도가 진짜 상한).  8/31 500VU OOM 87회의 근본 원인이던 "제출 경로 동시성 무제한"은 동기 경로 제거로 소멸했고, 이제 상한은 워커 세마포어 하나. 값은 부하테스트로
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
