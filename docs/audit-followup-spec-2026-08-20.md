# 감사 재리뷰 후속 수정 명세

> 작성일: 2026-08-20  
> 기준: 2026-08-19 수정 워킹트리를 재리뷰한 결과  
> 목적: 다음 구현 에이전트가 별도 해석 없이 남은 6건을 재현 테스트부터 완료할 수 있게 하는 실행 명세  
> 원칙: 이 문서는 구현 인계서다. 작성 과정에서 애플리케이션 코드는 수정하지 않았다.

## 1. 이 문서의 범위

2026-08-19 수정 묶음은 방향은 맞았지만 9개 재리뷰 항목 중 3개만 완전히 닫혔다. 이 문서(FU-01~06)로 후속 처리한 결과, FU-02/03/04/06 4건은 2026-08-20에 완료됐다.

| 구분 | 항목 |
|---|---|
| 검증 완료 — 재작업 금지 | POST + CSRF 로그아웃, 설문 주제 중복 거부, 관리자 CRUD 후 `PracticeAttemptService` DTO 캐시 제거 |
| ✅ 완료 (2026-08-20) | FU-02(SCORE-02 롤플레이 MP), FU-03(TEST-02 dev k6 rate limit), FU-04(API-01 multipart 오류), FU-06(AI-01 태그 중복) |
| 부분 완료 — 계속 진행 필요 | FU-01(DATA-01 finalize), FU-05(ADMIN-02 출제 가능 주제) |
| 계속 보류/미착수 — 이번 범위 밖 | TEST-01, API-03, OPS-01, DB-01, DESIGN-01 |

기존 캐시 적용의 성능 수치는 문제로 보지 않는다. 캐시 애노테이션 제거 과정도 이번 범위가 아니다.

시작 전 다음 문서를 순서대로 읽는다.

1. `AGENTS.md`
2. `DOMAIN.md`
3. `PROJECT.md`
4. `docs/codebase-risk-audit-2026-08-13.md`
5. 이 문서

현재 수정들은 작성 시점에 커밋되지 않은 워킹트리 기준이었다. 구현 에이전트는 먼저 `git status --short`와 `git diff`로 실제 시작 상태를 확인하고, 이미 존재하는 사용자 변경을 덮어쓰지 않는다.

## 2. 상태와 권장 작업 순서

| ID | 연결 항목 | 우선순위 | 준비도 | 판정 |
|---|---|---:|---|---|
| FU-01 | DATA-01 | P1 | MIXED | 일반 rollback/단일 JVM 경합만 해결, DB 기준 멱등성 미완료 |
| FU-02 | SCORE-02 | P1 | ✅ 완료(2026-08-20) | 짧은 응답 분기까지 닫음 — `FeedbackServiceRoleplayMainPointTest` |
| FU-03 | TEST-02 | P2 | ✅ 완료(2026-08-20) | dev+null-member 조합만 소비 스킵으로 변경 — `RateLimiterServiceDevProfileTest`. k6 실제 재실행 검증은 별도 |
| FU-04 | API-01 | P2 | ✅ 완료(2026-08-20) | handler 탐색 전 예외 전담 advice 신설 + order 고정 — `MultipartFrameworkExceptionIntegrationTest` |
| FU-05 | ADMIN-02 | P2 | READY | 유형별 연습만 해결, 나머지 진입점 미완료 |
| FU-06 | AI-01 | P2 | ✅ 완료(2026-08-20) | 답변 단위 distinct + coaching feedbackId 집계 — `FeedbackServiceScoreValidationTest`, `CoachingServiceDistinctOccurrenceTest` |

권장 순서는 `FU-02 → FU-06 → FU-03 → FU-04 → FU-05 → FU-01`이다.

- FU-02와 FU-06은 둘 다 `FeedbackService`를 수정하므로 같은 에이전트가 순서대로 처리한다.
- FU-05는 여러 컨트롤러와 `MockExamService`를 건드리므로 별도 작업 단위로 둔다.
- FU-01은 실제 DB transaction 테스트가 필요하므로 마지막에 독립적으로 처리한다.
- 여러 에이전트가 같은 워킹트리를 공유하면 Gradle 테스트를 동시에 실행하지 않는다. 테스트 결과 XML과 Gradle lock이 충돌할 수 있다.

모든 티켓은 아래 순서를 지킨다.

1. 이 문서의 실패 시나리오를 재현하는 테스트를 먼저 추가한다.
2. 수정 전 해당 테스트가 의도한 이유로 실패하는지 확인한다.
3. 최소 구현으로 테스트를 통과시킨다.
4. 티켓에 적은 기존 회귀 테스트도 함께 실행한다.
5. 완료 조건을 전부 확인한 뒤에만 감사 문서를 `완료`로 바꾼다.
6. 기능 수정이 끝나면 `docs/CHANGELOG.md`에 후속 완료 이력을 새 줄로 추가하고 `docs/backlog.md`를 갱신한다. 과거 CHANGELOG 항목은 고치지 않는다.

---

## 3. FU-01 — DB 기준 finalize 멱등·원자성

- 연결 항목: DATA-01
- 우선순위: P1
- 현재 상태: 부분 완료

### 현재 보존할 동작

- `IN_PROGRESS → FINALIZING → SUBMITTED` 상태 구분
- Caffeine의 원자적 `computeIfPresent` 전이
- feedback과 tag를 하나의 Spring `@Transactional` 서비스에서 저장
- 명확한 DB rollback 뒤 `IN_PROGRESS` 복구
- 정상 완료된 Caffeine entry에 대한 동일 결과 URL 재응답

### 남은 실패 계약

`PracticeAttemptApiController.finalize()`는 DB transaction을 먼저 끝내고 별도 Caffeine 전이로 `SUBMITTED`를 확정한다.

- DB commit은 성공했지만 호출자에게 예외가 전달되면 현재 catch가 `IN_PROGRESS`로 되돌릴 수 있다.
- 이후 재시도가 같은 feedback/tag 배치를 다시 저장할 수 있다.
- `confirmSubmitted()==false`여도 현재 요청은 성공으로 반환한다.
- Caffeine entry가 evict되거나 프로세스가 재시작되면 DB에 완료 데이터가 있어도 동일 완료 요청을 판정할 source of truth가 없다.

관련 코드:

- `src/main/java/com/opicnic/opicnic/controller/PracticeAttemptApiController.java`
- `src/main/java/com/opicnic/opicnic/service/attempt/FeedbackPersistenceService.java`
- `src/main/java/com/opicnic/opicnic/service/attempt/CaffeinePracticeAttemptStore.java`
- `src/main/java/com/opicnic/opicnic/domain/FeedbackResult.java`

### 권장 최소 설계

Caffeine의 `FINALIZING`은 단일 JVM에서 빠르게 중복 요청을 차단하는 보조 상태로 유지한다. 최종 완료의 source of truth는 DB marker로 바꾼다.

신규 aggregate는 상태 머신 없이 다음 필드만 가진다.

```text
practice_attempt_finalization
- attempt_id    varchar(36) primary key
- member_id     bigint nullable
- finalized_at  timestamp not null
```

- `attempt_id` PK가 attempt 단위의 DB 멱등 키다.
- `member_id=null`은 dev 전용 anonymous attempt만 허용한다.
- persistent `FINALIZING` 상태나 lease/reaper는 이번 최소안에 추가하지 않는다.

`FeedbackPersistenceService`의 하나의 실제 Spring transaction 안에서 다음 순서를 실행한다.

```text
회원/소유자 확인
→ finalization marker INSERT + flush
→ FeedbackResult 저장
→ FeedbackTag 저장
→ 한 번에 COMMIT
```

marker, feedback, tag 중 하나라도 실패하면 셋 모두 rollback되어야 한다. marker PK는 같은 attempt의 두 번째 배치 진입을 DB에서 차단한다. 기존 감사의 `(attemptId, questionOrdinal) 또는 동등한 DB unique` 조건에서 이 marker PK를 동등한 batch idempotency key로 사용한다.

신규 파일의 권장 위치:

- `src/main/java/com/opicnic/opicnic/domain/attempt/PracticeAttemptFinalization.java`
- `src/main/java/com/opicnic/opicnic/repository/PracticeAttemptFinalizationRepository.java`

### finalize 요청 처리 순서

1. 현재 OAuth principal의 member ID를 해석한다.
2. Caffeine attempt보다 먼저 DB marker를 조회한다.
   - marker가 있고 owner가 같으면 persistence를 다시 호출하지 않고 기존 결과 URL을 반환한다.
   - marker owner가 다르면 기존 소유권 노출 정책에 맞춰 403 또는 404로 거부한다.
3. marker가 없을 때만 Caffeine attempt의 owner, expiry, 결과 완성도를 검증한다.
4. `IN_PROGRESS → FINALIZING` CAS를 시도한다.
   - 성공하면 DB finalize transaction을 실행한다.
   - 실패 후 marker가 존재하면 같은 결과 URL을 반환한다.
   - 실패하고 marker도 없으면 현재 처리 중이므로 409를 반환한다. 410과 섞지 않는다.
5. DB transaction이 성공하면 Caffeine `FINALIZING → SUBMITTED`는 best-effort 정합화로 실행한다.
   - `confirmSubmitted()==false`를 무시하지 말고 warning을 남긴다.
   - 성공 판정은 이미 커밋된 DB marker이므로 응답은 성공으로 유지한다.
6. DB 호출이 예외를 던지면 Caffeine을 재시도 가능한 상태로 되돌린 뒤, 새 transaction으로 marker를 조회한다.
   - marker 존재: commit 성공/ACK 유실로 판정하고 같은 결과 URL을 반환한다.
   - marker 없음: 실제 rollback으로 판정하고 원래 예외를 전달한다.
   - marker 조회도 실패: 성공을 추측하지 않고 5xx로 반환한다. 다음 요청은 다시 marker부터 확인한다.

처리 중 상태는 전용 예외를 만들어 409로 매핑한다. 현재 모든 `IllegalStateException`을 410으로 보내는 계약에 억지로 넣지 않는다.

`FeedbackPersistenceService`가 회원을 찾지 못했을 때 조용히 return하면 marker 없는 성공 응답이 생긴다. 이 경우는 transaction을 rollback시키는 명시적 예외로 바꾼다.

### 필수 회귀 테스트

1. **동시 finalize 2개**
   - latch로 두 요청을 동시에 시작한다.
   - marker 1행, feedback 정확히 N행, tag 정확히 기대 개수다.
   - 한 요청은 성공하고 다른 요청은 같은 결과 또는 처리 중 409다.
   - 409 요청을 다시 보내면 같은 결과 URL을 받는다.

2. **feedback 저장 후 tag 실패**
   - 실제 Spring transaction proxy와 MySQL Testcontainers를 사용한다.
   - tag insert 실패를 유도한다.
   - marker, feedback, tag가 모두 0행이다.
   - Caffeine 상태는 재시도 가능하며 다음 finalize가 성공한다.

3. **commit 성공 후 ACK 유실**
   - 실제 marker와 결과 commit 뒤 호출자에게 예외가 전달된 상황을 재현한다.
   - controller가 marker를 조회해 성공으로 수렴한다.
   - 재요청에도 행 수가 늘지 않는다.

4. **cache confirm 실패와 eviction**
   - DB commit 뒤 `confirmSubmitted=false`를 반환하게 한다.
   - 첫 요청은 성공한다.
   - Caffeine entry를 제거한 뒤 같은 owner가 다시 finalize해도 같은 URL을 받는다.
   - persistence는 다시 호출되지 않는다.

5. **명확한 rollback**
   - marker 없이 persistence가 실패한다.
   - Caffeine은 `IN_PROGRESS`, 세션 결과는 유지된다.
   - 동일 세션 재시도가 성공한다.

6. **owner**
   - Caffeine entry가 없는 완료 attempt도 같은 owner는 성공한다.
   - 다른 owner는 marker 존재 여부를 추측할 수 없는 기존 403/404 정책으로 거부한다.

transaction 검증 테스트는 persistence 서비스를 `new`로 만들거나 전체 mock으로 대체하지 않는다. Spring proxy를 실제로 거쳐야 한다.

### 완료 조건

- attempt별 finalization marker는 최대 1행이다.
- feedback/tag 배치는 0회 또는 정확히 1회만 commit된다.
- marker·feedback·tag의 부분 commit이 없다.
- rollback 뒤 즉시 재시도할 수 있다.
- commit 결과가 불명확해도 marker 조회로 중복 없이 판정한다.
- cache confirm 실패나 eviction 뒤에도 같은 owner의 완료 재요청은 같은 URL을 받는다.
- 기존 finalize/store 단위 테스트와 위 transaction 테스트가 모두 통과한다.

### 범위 밖

- answers subset의 세션 map merge 경합
- 새 세션/새 기기에서 결과 내용을 복구하는 resultId 기반 화면
- mock ordinal, combo slot, provenance 장기 영속화
- 진행 중 attempt/session의 Redis 이전
- Flyway/Liquibase 도입

DB marker는 같은 결과 URL을 재응답하는 계약까지만 해결한다. URL을 다시 받은 새 세션에서 결과 내용을 복원하는 문제는 제품 감사 PC-12의 별도 범위다.

---

## 4. FU-02 — 짧은 롤플레이 응답도 MP 평가 제외

- 연결 항목: SCORE-02
- 우선순위: P1
- 현재 상태: ✅ 완료 (2026-08-20) — 테스트: `FeedbackServiceRoleplayMainPointTest`

### 남은 실패 계약

`FeedbackService`는 STT 결과가 5단어 미만이면 question type을 확인하기 전에 `noResponseDto()`를 반환한다. 현재 `noResponseDto()`는 모든 문항에 `mainPointScore=1`을 넣는다. 따라서 TYPE_5~7의 짧은 답변만 다시 핵심전달 1점 표본으로 저장된다.

### 권장 최소 구현

- `noResponseDto()`가 `QuestionDto.questionType`을 확인한다.
- TYPE_5, TYPE_6, TYPE_7이면 `mainPointScore=null`을 사용한다.
- 그 외 유형의 짧은 답변은 기존 `mainPointScore=1`을 유지한다.
- 표현력, 정확성, 발화량, 내용전개의 1점과 `overallGrade=IL`은 변경하지 않는다.
- 정상 길이 답변과 같은 `isRoleplayType()` helper를 사용해 규칙을 이중화하지 않는다.

### 필수 회귀 테스트

`FeedbackServiceRoleplayMainPointTest`에 다음을 추가한다.

- TYPE_5, TYPE_6, TYPE_7 parameterized test
- 빈 문자열과 1~4단어 STT 결과
- `mainPointScore == null`
- 나머지 네 점수는 1, grade는 IL
- 짧은 응답에서는 Groq 채점/태깅이 호출되지 않음
- TYPE_1의 짧은 답변은 MP 1점을 유지

기존 아래 테스트도 함께 통과해야 한다.

- `FeedbackServiceRoleplayMainPointTest`의 정상 길이 케이스
- `ExamPlanServiceRoleplayOnlyDiagnosisTest`
- `AnalyticsControllerRoleplayOnlyTest`

### 완료 조건

- 길이와 관계없이 저장된 TYPE_5~7의 `mainPointScore`는 항상 null이다.
- 롤플레이만 연습한 사용자의 핵심전달이 진단/Analytics 최약점에 나타나지 않는다.
- 비롤플레이 무응답 점수 정책은 변하지 않는다.

### 범위 밖

- 5단어 기준 변경
- 무응답 등급 정책 재설계
- TYPE_8 MP 정책 변경
- 전체 등급 산식 변경

---

## 5. FU-03 — dev k6 attempt만 rate limit에서 제외

- 연결 항목: TEST-02
- 우선순위: P2
- 현재 상태: ✅ 완료 (2026-08-20) — 테스트: `RateLimiterServiceDevProfileTest`. k6 `error_rate < 0.05` 실제 재실행 검증은 별도 수행 필요(이번엔 코드/단위 테스트까지만)

### 남은 실패 계약

현재 dev의 모든 비로그인 VU는 이름만 다른 하나의 `dev-loadtest` 버킷을 공유한다. 용량은 시간당 15문항 그대로라 20→100 VU 스크립트는 최초 몇 요청 뒤 대부분 429가 된다. 현재 `RateLimiterServiceDevProfileTest`는 오히려 dev의 16번째 소비 실패를 기대해 이 상태를 고정한다.

### 권장 최소 구현

`DevPracticeController`가 만든 attempt는 dev profile에서만 존재하고 `memberId=null`이다. 다음 두 조건이 동시에 참일 때만 비용 한도를 소비하지 않는다.

1. 현재 profile이 dev다.
2. 현재 attempt의 `memberId == null`이다.

이를 위해 controller가 rate limiter에 attempt/member 정보를 전달하도록 계약을 바꾼다. 임의 HTTP 헤더를 신뢰해 우회시키지 않는다.

- dev의 로그인 회원 attempt는 기존 시간당 15문항 한도를 유지한다.
- production의 anonymous 요청도 기존 한도를 유지한다.
- 단순히 또 다른 유한 공유 버킷을 만드는 방식은 사용하지 않는다.
- profile 판정은 active profile 배열만 직접 보지 말고 `Environment.acceptsProfiles(Profiles.of("dev"))`를 사용한다. 현재 `spring.profiles.default=dev`도 동일하게 인식해야 한다.

### 필수 회귀 테스트

`RateLimiterServiceDevProfileTest`를 결과 계약으로 다시 작성한다.

- active dev + `memberId=null`: `CAPACITY_PER_HOUR + 100`회 모두 성공
- default dev + `memberId=null`: 동일하게 모두 성공
- active dev + 실제 member ID: 15회 성공, 16번째 실패
- production + `memberId=null`: 15회 성공, 16번째 실패
- controller가 현재 attempt 정보를 limiter에 전달함

최종 검증은 실제로 아래 환경에서 수행한다.

```text
SPRING_PROFILES_ACTIVE=dev
STT_ENABLED=false
LLM_ENABLED=false
k6 run scripts/load-test.js
```

### 완료 조건

- k6 실행에서 rate limiter가 만든 429는 0건이다.
- 스크립트의 `error_rate < 0.05` 기준을 통과한다.
- production과 dev 로그인 회원의 기존 한도는 유지된다.
- dev 전용 null-member attempt 이외에는 우회되지 않는다.
- 실행 환경, payload, VU, duration, mock AI 여부를 재현 문서에 기록한다.

### 범위 밖

- production 한도 15 조정
- Redis 기반 분산 limiter
- IP/세션별 anonymous 식별 정책
- k6 p95 목표 변경
- live AI 부하테스트

---

## 6. FU-04 — handler 탐색 전 multipart 실패도 공통 4xx

- 연결 항목: API-01
- 우선순위: P2
- 현재 상태: ✅ 완료 (2026-08-20) — 테스트: `MultipartFrameworkExceptionIntegrationTest`, `ApiExceptionHandlerMultipartTest`. 재리뷰로 추가 발견된 order 문제(handler 확정 후 컨트롤러 내부 예외가 catch-all에 먼저 잡히던 것)도 `@Order(HIGHEST_PRECEDENCE)`로 함께 해결

### 현재 보존할 동작

- 깨진 JSON 400
- path/enum 타입 불일치 400
- JSON endpoint의 지원하지 않는 Content-Type 415
- controller 진입 뒤 명시적 payload/file 검증 4xx

### 남은 실패 계약

`ApiExceptionHandler`는 `@RestControllerAdvice(annotations = RestController.class)`로 제한된다. 기본 eager multipart parsing의 용량 초과는 controller를 찾기 전에 발생하므로 handler type이 null이다. selector가 있는 advice는 이 예외에 적용되지 않는다.

따라서 handler 메서드를 직접 호출하는 단위 테스트는 통과해도 실제 oversized HTTP 요청의 공통 `ErrorResponse` 계약을 증명하지 못한다.

### 권장 최소 구현

multipart framework 예외만 담당하는 selector 없는 별도 `@RestControllerAdvice`를 추가한다.

- `MaxUploadSizeExceededException` → 413
- 애플리케이션 `PayloadTooLargeException` → 413
- `MissingServletRequestPartException` → 400
- 일반 `MultipartException` → 400

위 매핑을 기존 scoped advice에서 새 advice로 이동해 중복 매핑을 만들지 않는다. 새 advice에는 catch-all을 두지 않는다. 기존 view/API 예외 경계를 넓히지 않고, handler type을 알 수 없는 multipart framework 예외만 전역에서 처리한다.

### 필수 회귀 테스트

핸들러 메서드 직접 호출만으로 완료 처리하지 않는다. MockMvc application context에 테스트 전용 `MultipartResolver`를 등록하고 `resolveMultipart()` 단계에서 예외를 던지게 해 실제 DispatcherServlet 순서를 검증한다.

- handler 탐색 전 `MaxUploadSizeExceededException` → 413
- JSON Content-Type
- body가 공통 `ErrorResponse`, message는 “첨부파일이 너무 큽니다.”
- controller/service 미호출
- handler 탐색 전 일반 `MultipartException` → 400 공통 body
- 필수 part 누락 → 400 공통 body
- 기존 text/plain JSON 요청 → 415 유지
- 정상 크기 multipart → 기존 controller까지 도달

### 완료 조건

- eager multipart 용량 초과도 413 공통 `ErrorResponse`를 반환한다.
- multipart client 오류가 catch-all 500 로그에 포함되지 않는다.
- 정상 API와 view controller의 기존 동작은 변하지 않는다.

### 범위 밖

- 서버 150MB 한도 변경
- 문항당 15MB 정책 변경
- streaming upload 전환
- MIME 보안/바이러스 검사 확대

---

## 7. FU-05 — 모든 연습 진입점이 실제 조립 가능성을 사용

- 연결 항목: ADMIN-02
- 우선순위: P2
- 현재 상태: 부분 완료

### 남은 실패 계약

`QuestionAssemblyService.assemble()`과 `assembleSingle()`은 topic이 선택된 뒤 불완전 set을 거른다. `/practice/type`도 요청 type을 낼 수 있는 topic만 고르도록 수정됐다.

그러나 아래 경로는 여전히 “QuestionSet 행이 하나라도 존재함”만 뜻하는 `findExistingTopics()`를 출제 가능 조건으로 사용한다.

- 홈 랜덤 연습
- 홈 돌발 연습
- 주제별 콤보
- 카테고리별 콤보
- 집중연습 후보 화면
- 모의고사 일반/돌발 topic 배정

빈 set뿐인 topic이 먼저 선택되면 다른 정상 topic이 있어도 연습이 실패한다. 모의고사는 `assemble()`의 `IllegalArgumentException`이 500으로 노출될 수 있다.

### 권장 최소 설계

`QuestionAssemblyService`에 다음 capability를 추가한다.

```text
canAssemble(SurveyTopic topic, ComboPattern pattern)
```

- 기존 `setCache`와 `hasAllTypes()`를 재사용한다.
- 필요한 type을 한 set 안에 모두 가진 활성 set이 하나라도 있으면 true다.
- type들이 여러 불완전 set에 흩어져 있으면 false다.
- 빈 set과 완전한 set이 섞여 있으면 true다.

`ComboPracticeService`는 random pattern을 고르기 전에 현재 topic으로 조립 가능한 pattern만 남긴다.

- 주제별: topic과 호환되는 pattern만 선택한다.
- 카테고리별: 요청 category이면서 topic과 호환되는 pattern만 선택한다.
- 호환 pattern이 없으면 다른 category로 대체하지 않고 제어된 `IllegalStateException`을 낸다.

각 controller는 `findExistingTopics()`만으로 출제 가능성을 판정하지 않는다.

- 랜덤/돌발: 현재 difficulty에서 하나 이상의 pattern을 실제 조립 가능한 topic만 추첨
- 직접 topic: 현재 difficulty에서 호환 pattern이 하나 이상인지 확인
- category: 해당 category를 실제 조립 가능한 사용자 topic만 추첨
- focus: 완전히 출제 불가능한 topic을 숨기고, 가능한 사용자 topic이 없는 category도 클릭 가능하게 노출하지 않음
- type: 현재 `hasQuestionType()` 필터 유지

모의고사는 pattern을 먼저 알고 있으므로 각 슬롯에 호환 topic을 배정한다.

- 일반 3슬롯은 일반 주제 풀을 유지한다.
- 돌발 2슬롯은 돌발 전용 풀을 유지한다.
- 기존 topic 중복 방지 계약을 유지한다.
- 후보를 무작위화한 뒤 5개 슬롯에 중복 없는 배정을 찾는다.
- 슬롯 수가 5개뿐이므로 이 용도에 한정한 작은 backtracking이면 충분하다. 범용 solver 추상화를 만들지 않는다.
- 배정 불가능하면 `IllegalStateException`으로 통일한다.
- controller도 방어적으로 조립 `IllegalArgumentException`을 500으로 흘리지 않는다.

관련 변경 지점:

- `QuestionAssemblyService`
- `ComboPracticeService`
- `HomeController`
- `PracticeComboController`
- `PracticeFocusController`
- `MockExamService`
- 필요하면 focus template

### 필수 회귀 테스트

**QuestionAssemblyService**

- 빈 set만 존재 → `canAssemble=false`
- 필요한 type 일부만 존재 → false
- 빈 set과 완전한 set 혼재 → true
- 필요한 type이 여러 set에 흩어짐 → false

**ComboPracticeService**

- 호환되지 않는 pattern은 반복 추첨에서도 선택되지 않음
- 요청 category에 호환 pattern이 없으면 다른 category로 대체하지 않음
- 호환 pattern이 하나면 정상 조립

**각 진입점**

- 불완전 topic과 완전 topic 혼재 시 랜덤/돌발/category는 완전 topic만 선택
- focus model에 완전히 불가능한 topic이 없음
- category를 낼 사용자 topic이 없으면 비노출 또는 제어된 redirect
- 기존 `PracticeTypeControllerTest` 유지

**모의고사**

- 일반/돌발 풀에 불완전 topic이 섞여 있어도 자기소개 포함 15문항 생성
- slot별 호환 주제가 다른 fixture에서도 가능한 배정을 찾음
- 후보가 부족하면 `?noTopics=true` 계열의 제어된 redirect
- 불완전 데이터가 500, `IndexOutOfBoundsException`, 조립 예외로 노출되지 않음
- 일반 3 + 돌발 2와 기존 topic 중복 정책 유지

### 완료 조건

- 랜덤, 돌발, 주제별, category별, type별, focus, mock 모두 실제 조립 가능성을 기준으로 후보를 고른다.
- 빈 set을 만들어도 다른 정상 topic이 있는 사용자 연습을 실패시키지 않는다.
- 명시적으로 요청한 category를 조용히 바꾸지 않는다.
- 관리자 CRUD 후 기존 두 캐시 무효화 계약을 유지한다.
- 위 회귀 테스트가 모두 통과한다.

### 범위 밖

- 관리자 질문 CRUD 또는 draft workflow
- completeness DB 컬럼/migration
- `findExistingTopics()` 자체의 전면 재설계
- ComboPattern/OPIc 규칙 변경
- 캐시 TTL/구조 변경

---

## 8. FU-06 — AI 태그를 답변 단위로 중복 제거

- 연결 항목: AI-01
- 우선순위: P2
- 현재 상태: ✅ 완료 (2026-08-20) — 테스트: `FeedbackServiceScoreValidationTest`, `CoachingServiceDistinctOccurrenceTest`

### 현재 보존할 동작

- category/type별 `FeedbackTagVocabulary` allowlist
- blank/unknown tag 거부
- category당 최대 5개의 허용 tag
- TYPE_5~7 mainPoint tag 제외

### 남은 실패 계약

현재 `FeedbackService.addTags()`는 허용된 동일 tag를 다섯 번까지 그대로 추가한다. `CoachingService`는 tag row 수를 발생 횟수로 세므로 한 답변의 `TENSE_ERROR` 5개만으로 `MIN_PATTERN_COUNT=3`을 충족한다.

### 권장 최소 구현

1. parsing 단계에서 category별 중복을 제거한다.
   - allowlist를 통과한 tag에만 `LinkedHashSet<String> seen`을 적용한다.
   - 처음 등장한 tag만 결과에 추가한다.
   - duplicate, blank, unknown은 상한을 소비하지 않는다.
   - `MAX_TAGS_PER_CATEGORY=5`는 허용된 고유 tag 기준이다.
   - 서로 다른 category는 별도로 판단한다.

2. coaching 집계도 feedback ID 기준으로 방어한다.
   - element 집계 키별 `Set<feedbackResultId>` 크기를 occurrence로 사용한다.
   - type 집계도 같은 기준을 사용한다.
   - 기존 DB에 중복 row가 남아 있어도 한 답변이 3회 패턴을 혼자 만들지 못한다.
   - 서로 다른 세 답변의 같은 tag는 정상적으로 3회다.

3. 현재 vocabulary, prompt, model, threshold는 바꾸지 않는다.

### 필수 회귀 테스트

- accuracy에 `TENSE_ERROR` 6개 → DTO tag 정확히 1개
- unknown, blank, duplicate, 정상 tag 혼합 → 허용된 고유 tag만 최초 등장 순서로 유지
- 서로 다른 허용 tag 4개 → 4개 유지
- TYPE_5~7 mainPoint tag → 계속 0개
- 같은 feedback ID에 동일 DB tag row 5개 → coaching occurrence 1
- 서로 다른 feedback 3개에 동일 tag → occurrence 3, 기존 threshold 충족
- type 비율의 분자도 서로 다른 feedback 수 사용
- persistence capture에 동일 `(feedbackResult, category, tag)`가 두 번 없음

기존 `FeedbackServiceScoreValidationTest.tagCountPerCategoryIsCapped`는 `size <= 5`만 확인하면 안 된다. 반복 fixture에 대해 `containsExactly("TENSE_ERROR")`처럼 중복 제거 결과를 직접 고정한다.

### 완료 조건

- 한 답변에는 같은 `(category, tag)`가 최대 한 번 저장된다.
- 한 답변만으로 `MIN_PATTERN_COUNT=3`을 충족할 수 없다.
- 서로 다른 세 답변은 정상적으로 3회로 집계된다.
- allowlist와 고유 tag 상한이 유지된다.
- unknown tag가 DB나 coaching 입력에 들어가지 않는다.

### 범위 밖

- vocabulary/prompt 재설계
- coaching 임계값·비율 정책 변경
- 기존 중복 row 물리 삭제
- DB unique/migration
- malformed JSON 정책 변경

---

## 9. 최종 검증과 완료 선언

### 최소 타깃 테스트 묶음

다음 기존 테스트와 각 티켓의 신규 테스트를 한 Gradle process에서 실행한다.

- `PracticeAttemptApiControllerFinalizeTest`
- `CaffeinePracticeAttemptStoreTest`
- 신규 finalization transaction integration test
- `FeedbackServiceRoleplayMainPointTest`
- `ExamPlanServiceRoleplayOnlyDiagnosisTest`
- `AnalyticsControllerRoleplayOnlyTest`
- `RateLimiterServiceDevProfileTest`
- `ApiExceptionHandlerTest`
- multipart pre-handler integration test
- `PracticeTypeControllerTest`
- `QuestionAssemblyServiceIncompleteSetTest`
- 신규 ComboPracticeService/각 진입점/MockExamService capability 테스트
- `AdminQuestionSetApiControllerCacheTest`
- `PracticeAttemptServiceQuestionCacheTest`
- `FeedbackServiceScoreValidationTest`
- 신규 CoachingService tag distinct 테스트

### 문서 완료 조건

코드와 테스트가 모두 통과한 뒤에만 다음을 수행한다.

1. `docs/codebase-risk-audit-2026-08-13.md`에서 해당 항목을 완료로 변경한다.
2. 이 문서의 티켓 상태를 완료로 바꾸거나, 모두 끝났다면 문서 상단에 완료일을 기록한다.
3. `docs/backlog.md`의 여섯 항목을 Done으로 이동한다.
4. `docs/CHANGELOG.md`에 실제 완료된 후속 변경을 새 줄로 추가한다.
5. controller/service 역할이 실제로 바뀌었다면 `PROJECT.md` 코드베이스 지도를 갱신한다.
6. `git diff --check`를 실행한다.

TEST-01은 보류 항목이므로 이번 티켓 묶음이 전체 suite/CI gate까지 해결했다고 주장하지 않는다. 타깃 테스트가 통과했다는 사실과 전체 suite가 검증됐다는 주장을 구분한다.

### 이 명세 작성 시 검증한 기준선

2026-08-20 재리뷰에서 기존 타깃 17개 클래스, 72개 테스트는 모두 통과했다. 그럼에도 위 6건이 남은 이유는 기존 테스트가 실패 경계를 직접 재현하지 않았기 때문이다. “테스트 통과”만으로 완료 처리하지 말고 이 문서의 각 실패 fixture가 실제로 추가됐는지 확인한다.
