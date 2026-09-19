# Backlog

**열린 작업만 둔다.** 끝난 건 `CHANGELOG.md`에 한 줄 적고 여기서 지운다 — Done 목록을 여기 쌓지 않는다(2026-09-18 전까지 그렇게 쌓여서 357줄이 됐다). 결정의 근거는 `adr/`, 검증 기준은 `performance/slo.md`.

## 지금 — R2 + 비동기 전환 (ADR-0001)

순서대로. "전" 측정은 SLO 판정이 아니라 현상 기록이다(`slo.md` 전/후 표 참고).

- [x] S1 스크립트 + 전환 전 S1 — `scripts/s1.sh`, 7.6s (`performance/2026-09-18/s1-before.txt`)
- [x] 전환 전 S2·S3 — 증폭 1.50배(SLO 1.65), 동시 30에서 p95 7.56s로 1명과 동일 (`performance/2026-09-18/`). 동기의 문제는 속도가 아니라 유실·실패 시 사용자 재제출로 확정
- [x] 1단계: 잡 테이블 `ScoringJob`/`ScoringJobItem` + `AudioStorage`(R2/인메모리) — 2026-09-18
- [x] 2단계: 접수 API 3개 (`upload-urls` / `POST /api/scoring-jobs` 202 / `GET` 폴링) — 2026-09-19
- [x] 3단계: `ScoringWorker` — 이탈·재시작(접수 직후/처리 중) 완료율 100% 확인 (`scripts/s1-async.sh`) — 2026-09-19
- [ ] **R2 + 비동기 구현 (나머지)** — [`adr/0001-async-r2.md`](adr/0001-async-r2.md) 4절. 대화에서 정한 구현 결정: dev 테스터 회원(nullable 대신) / 워커 동시 상한 설정값 30 / 숏폴링 2초, SSE는 후속 / submit 시 R2 HEAD 안 함 / URL 발급은 녹음 후(서명에 크기 포함) / **잡 행은 submit에서 생성**(화면 열 때 아님 — 약속 전엔 Caffeine) / 경로 `/api/scoring-jobs`. 남은 순서: **화면**(question.html 모의고사 분기 R2 직접 업로드 + 폴링 결과 화면) → 정리(모의고사 경로에서 finalize·세션·beforeunload 제거, PROJECT.md·ADR 결과 절) → 전환 후 S1~S4 측정.
  - 후속(1단계 범위 밖): 처리 후 오디오 `pending/`→`attempts/` 복사(30일 보관, ADR 3절). 지금은 pending/ 라이프사이클 1일로 지워짐
  - 후속: `COMPLETED_WITH_FAILURES` 문항의 사용자 재시도 API (`POST /api/scoring-jobs/{id}/items/{index}/retries`) presigned URL(`content-length-range` 4MB, 소유자·문항 범위, 10분 만료, 키는 서버가 `pending/{attemptId}/q{n}.webm`) → submit 시 R2 내부 복사로 `attempts/` → 잡 테이블(DB) + 가상 스레드 워커 폴링 → 문항별 즉시 저장 + 상태값 → finalize 제거 → 재시도 3회 상한 + FAILED 확정 + 워커 실패율 서킷. 콤보는 1단계에서 동기 유지, 업로드만 R2로
  - 이때 같이: dev attempt(memberId=null)도 DB에 저장되게 — 안 그러면 S1의 kill/restart → DB 검증이 성립 안 함
  - VM `.env`에 R2 키 4개 넣기 (로컬 `.env`에만 있음)
- [ ] 전환 후 S1(kill·restart 포함)~S4 → `slo.md` 전/후 표 완성
- [ ] 블로그 초안 — "빠른 것과 안전한 것은 다르다"(S1 7.6s인데 완료율 0%)
- [ ] 2단계: 콤보도 같은 경로로, 동기 경로 제거

## 운영 — 외부 LLM 제공자

- [ ] **모델 소멸 감지 장치가 없다.** 7/31, 8/31 두 번 다 우연히 발견. 응답 시간·힙·에러율 전부 정상이고 사용자만 실패 카드를 본다. 기동 시 모델 존재 확인이나 채점 실패율 알림 중 하나는 필요 (`ScoringFailureRateHigh` 알림이 9/18 생겼으니 그걸로 잡히는지 확인)
- [ ] 답변 제출 경로에 동시 요청 수 상한이 없다 — 8/31 500VU OOM 87회의 근본 원인. 허용치는 부하테스트로 산정, 구현 방식(세마포어 등)은 그 다음. 비동기 전환 후엔 워커 동시성으로 대체될 수 있음
- [ ] (별건) axon 프로젝트 `axon-grafana`가 `0.0.0.0:3000`, `axon-mysql`이 `0.0.0.0:3306` 바인딩. 7/15 랜섬웨어와 같은 설정. 둘 다 `127.0.0.1:`로 — 그쪽에 전달

## 감사 후속 (archive/audit-followup-spec-2026-08-20.md 잔여)

- [ ] FU-01 / DATA-01: DB finalization marker를 source of truth로 둔 finalize 멱등성 — **비동기 전환하면 finalize 자체가 없어지므로 그때 자연 해소. 먼저 하지 말 것**
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
