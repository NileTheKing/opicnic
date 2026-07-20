# PROJECT.md

OPICnic 코드베이스 지도. 컨트롤러/서비스를 처음부터 grep하지 않고 어디에 뭐가 있는지 바로 찾기 위한 문서. 코드가 바뀌면 이 파일도 같이 고친다(`AGENTS.md`의 Documentation Checkpoint 참고). OPIc 시험 규칙 자체는 `DOMAIN.md`, 협업 규약은 `AGENTS.md` 참고.

아래 표는 룩업용이고, 요청이 실제로 어떻게 흘러가는지는 "핵심 흐름"을 먼저 볼 것. (README.md의 요청흐름 다이어그램은 포폴용으로 일부러 추상화돼있어서 클래스명이 없다 — 여기가 구현 레벨 버전.)

## 핵심 흐름

### 1. 콤보 연습 (제출 → 피드백)

```
PracticeComboController (/practice/combo)
  → PracticeAttemptService.createAttempt()   // attemptId 발급, questionIds를 Caffeine에 저장
  → question.html 렌더링

PracticeAttemptApiController (/api/practice-attempts/{attemptId}/...)
  → FeedbackService.getComboFeedbackStreaming()
      → StructuredTaskScope로 문항별 병렬 처리
      → STTService (Groq Whisper)
      → GroqService.getOpicFeedback()        // 채점
      → GroqService.extractFeedbackTags()    // 태깅
      → FeedbackResult + FeedbackTag 저장 (finalize 시점)
  → PracticeFeedbackController (/practice/feedback/result) 결과 화면
```

실패 문항 재시도(`/{attemptId}/answers/retry`)도 같은 `FeedbackService` 경로를 재사용한다 — attemptId로 원본 questionIds를 복원해서 재매핑.

### 2. 코칭 리포트 생성

```
CoachingController (POST /analytics/coaching)
  → CoachingService.generate(member)
      → 최근 FeedbackResult + FeedbackTag 조회
      → 요소별 집계 (count >= 3), 유형별 집계 (비율 >= 0.4)
      → GroqService.getCoachingReport()   // 집계 결과를 문장으로 서술만 함
      → CoachingReport 저장
```

개별 피드백 시점(흐름 1)에 이미 태깅까지 끝나있어서, 코칭 리포트 생성 시점엔 LLM을 판단 목적으로 다시 부르지 않는다 — 코드가 집계한다.

### 3. 모의고사 조립

```
HomeController (/practice/mock)
  → MockExamService.createMockExam(profile)
      → TopicCatalog (배경설문 22개 vs 돌발 23개 풀 구분)
      → OpicComboPatternProvider (난이도별 ComboPattern)
      → QuestionAssemblyService.assemble()  // QuestionSet + ComboPattern → QuestionDto
  → (이후 흐름 1의 PracticeAttempt 경로와 합류)
```

## 컨트롤러 → 라우트

| 컨트롤러 | 베이스 경로 | 성격 | 역할 |
|---|---|---|---|
| `HomeController` | `/` | View | 홈, `/practice/random`, `/practice/surprise`, `/practice/mock` 진입점 |
| `AuthController` | `/auth` | View | 로그인 페이지, 테스트용 인증 확인 |
| `OnboardingController` | `/onboarding` | View | 최초 가입 후 배경설문 |
| `MyPageController` | `/mypage` | View | 설정, 배경설문 수정, 관심 주제 토글 |
| `TopicsController` | `/practice/topics` | View | 주제 탐색 화면 |
| `PracticeComboController` | `/practice/combo` | View | 주제/카테고리 기반 콤보 연습 시작 |
| `PracticeTypeController` | `/practice/type` | View | 유형별 연습 (구상 단계, `docs/hold.md` 참고) |
| `PracticeFocusController` | `/practice/focus` | View | 집중 연습 모드 (구상 단계) |
| `PracticeFeedbackController` | `/practice/feedback/result` | View | 연습 결과 화면 |
| `PracticeAttemptApiController` | `/api/practice-attempts` | **REST API** | 답변 제출/재시도/확정 (`/{attemptId}/answers`, `/{attemptId}/answers/retry`, `/{attemptId}/finalize`) |
| `ExamController` | `/exam` | View | 시험 준비 계획 (학습 스케줄) |
| `AnalyticsController` | `/analytics` | View | 학습분석 탭 |
| `CoachingController` | `/analytics/coaching` | View | 코칭 리포트 목록/상세/생성 |
| `AdminController` | `/admin` | View | 질문 세트 관리 화면(뷰만, CRUD는 아래 API) |
| `AdminQuestionSetApiController` | `/api/admin/question-sets` | **REST API** | 질문 세트 생성/수정/삭제. `/api/admin/**`은 인증 필요(`SecurityConfig`에서 `/api/**` permitAll 예외 처리됨) |
| `EnumController` | `/api/enums` | **REST API** | 지역/주제/난이도 enum 목록 |

## 서비스 → 역할

| 서비스 | 역할 |
|---|---|
| `ComboPracticeService` | 주제+난이도로 짧은 연습 콤보 하나 생성 |
| `MockExamService` | 15문항 모의고사 생성 (자기소개 1 + 콤보 5) |
| `OpicComboPatternProvider` | 난이도별 `ComboPattern` 제공 |
| `QuestionAssemblyService` | `QuestionSet + ComboPattern` → `QuestionDto` 리스트 변환 |
| `ComboQuestionStrategy` / `FixedComboQuestionStrategy` / `OpicStandardComboSelectionStrategy` | 콤보 내 문제 선택 전략 |
| `TopicCatalog` | 배경설문 주제(22개)/돌발 주제(23개) 카탈로그 |
| `FeedbackService` | 답변 제출 처리 — STT/LLM 병렬 호출(`StructuredTaskScope`), 429 분리 백오프, 재시도 로직 |
| `GroqService` | Groq API 호출 — `getOpicFeedback`(채점), `extractFeedbackTags`(태깅), `getCoachingReport`(코칭 리포트 문장화) |
| `STTService` | Groq Whisper STT 호출 |
| `CoachingService` | 저장된 `FeedbackTag`를 요소별·유형별로 집계해 코칭 리포트 생성 (태그 아키텍처 — 클래스 상단 주석 참고) |
| `ExamPlanService` | 학습 이력 기반 시험 준비 계획/약점 유형 진단 |
| `MemberService` | 회원 가입/조회 |
| `CustomOAuth2UserService` | 카카오 OAuth2 로그인 연동 |

## 도메인 엔티티

| 엔티티 | 역할 |
|---|---|
| `QuestionSet` / `Question` | 주제별 문제 은행 (`TYPE_1~10`) |
| `Combo` | 영속 콤보 엔티티 — **주의: 이건 OPIc 출제의 source of truth가 아님.** 런타임 `ComboPattern`이 진짜 출제 로직 (`DOMAIN.md` 참고) |
| `Member` | 사용자 |
| `SurveyProfile` | 배경설문 응답 (거주형태, 관심 주제 등) |
| `FeedbackResult` | 답변 하나의 채점 결과 (표현력/정확성/메인포인트/유창성/내용 + quote/fix) |
| `FeedbackTag` | `FeedbackResult`에 붙는 태그 (코칭 리포트 집계용) |
| `CoachingReport` | 생성된 코칭 리포트 |
| `ExamSchedule` | 시험 준비 학습 스케줄 |
| `NotificationSetting` | 알림 설정 |

## PracticeAttempt / attemptId 설계 배경

`PracticeAttemptService.createAttempt()`는 서버가 문제를 조립한 뒤 `attemptId → questionIds[]`를 Caffeine 캐시(2시간 TTL)에 저장한다. 클라이언트는 `attemptId`만 받고, 제출 시 `attemptId`는 URL 경로(`/api/practice-attempts/{attemptId}/...`)로, 오디오는 별도 멀티파트 파일로 전송한다.

**주목적: 제출-재시도-finalize 3단계 멀티스텝 플로우 지원.**
실패 문항만 재제출할 때 서버가 원본 questionIds를 복원해야 retry 매핑이 가능하기 때문이다.
부수효과로 클라이언트가 question content나 ID를 조작할 수 없게 된다.

캐시(인메모리)를 쓰는 이유: 연습 완료 전의 임시 상태라 DB 영구 저장이 불필요하고, 서버 재시작 시 만료돼도 무해하다. 분산 캐시(Redis)로 교체 안 한 이유는 `docs/hold.md` 참고 — `PracticeAttemptStore` 인터페이스로 이미 분리해둬서 나중에 교체 가능.

## Important Constraints (이미 끝난 논쟁, 재검토 없이 지킬 것)

코드 동작 관련 규칙은 여기, 기술 선택(Kafka/Redis 등을 왜 안 썼는지) 근거는 `docs/hold.md` 참고.

- Do not hardcode topic counts such as `22` or `23`. Always derive from `TopicCatalog`.
- Do not reintroduce difficulty selection into the topic exploration page. Difficulty comes from onboarding/profile.
- Keep `docs/local/` ignored. It is for local development notes.
- `/api/**`는 Spring Security에서 기본 permitAll — 새 관리자/보호 API를 `/api/`로 추가할 땐 `SecurityConfig`에 명시적 예외 규칙을 먼저 추가할 것 (`AdminQuestionSetApiController` 참고).

## Verification Notes

- `./gradlew compileJava` currently passes.
- `./gradlew test` currently fails because `QuestionSetAdminIntegrationTest` requires Docker (Testcontainers). Docker not running = test fails. Code itself is correct.
