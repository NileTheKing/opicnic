# Feature Backlog

구현 예정이거나 고도화할 기능 목록.

---

## Practice Attempt / Feedback Retry

### Done

- `PracticeAttempt` 도입
- Caffeine 기반 `PracticeAttemptStore` 구현
- `attemptId -> questionIds/memberId/mode/status/expiresAt` 저장
- 제출/재시도 시 클라이언트가 보낸 question content를 신뢰하지 않고 `attemptId`로 서버에서 문제 복원
- 서버의 동일 `InputStream` 자동 재시도 제거
- 실패 문항만 브라우저가 보관 중인 녹음 Blob으로 재제출
- 제출 API 분리
- 재시도 API 분리
- finalize API 분리
- 결과 페이지 이동 전 `beforeunload` 이탈 경고 추가
- 지수 백오프 + Jitter 서버 자동 재시도 (max 3회, VirtualThread park 활용) — 2026-06-06
- dev/prod 프로파일 분리 — `/start`, `/sequential-benchmark`를 `@Profile("dev")` 전용 컨트롤러로 분리 — 2026-06-06
- 모바일 반응형 — 사이드바 `hidden md:flex`, 하단 bottom nav 추가 — 2026-06-06
- `restoreQuestionsForIndexes` ConcurrentHashMap 캐싱 + `@Transactional(readOnly=true)` 제거 — start p95 16.69s→20ms, answers p95 20.5s→3.73s — 2026-06-11
- Feedback 점수 필드 6개 (vocabularyScore~overallGrade) + 한국어 전용 프롬프트 + 테스트 7개 — 2026-06-11
- 주제탐색 카테고리화 (전체/내주제 카테고리별 섹션, 토글 DOM 즉시 반영) — 2026-06-12
- `POST /mypage/topics/toggle` API — 2026-06-12
- 온보딩 "전체 선택" 버튼 — 2026-06-12

### Current Shape

```text
문제 시작:
GET /practice/combo
GET /practice/mock
-> Thymeleaf Controller가 questions + attemptId 생성
-> question.html 렌더링

답변 제출:
POST /api/practice-attempts/{attemptId}/answers

실패 문항 재제출:
POST /api/practice-attempts/{attemptId}/answers/retry

결과 확정:
POST /api/practice-attempts/{attemptId}/finalize

결과 화면:
GET /practice/feedback/result
```

현재 구조는 `시작/결과 화면 = Thymeleaf`, `제출/재시도/finalize = API`인 중간 단계다. attemptId는 URL 경로로 승격, 응답은 타입 있는 DTO(`SubmissionResponseDto` 등)로 정리 완료 — 2026-07-14.

### Next

- `restoreQuestionsForIndexes` 캐싱 — 500 VU 부하테스트에서 발견된 병목. 매 제출마다 DB 조회 → ConcurrentHashMap 캐싱으로 해소 예정
- `HttpServletRequest#getParameter`, `getParts` 직접 파싱 제거
- 결과 누적용 session 제거 여부 결정
- `resultId` 기반 결과 조회 구조 검토
- IndexedDB에 녹음 Blob 임시 저장
- React 전환 시 `POST /api/practice-attempts` 시작 API 연결
- Caffeine store를 Redis store로 교체 가능한 구조 유지

---

## OPIc Mock Exam

### Done

- 난이도별 런타임 `ComboPattern` 적용
- Level 3~4: `[123] [123] [134] [674] [15]`
- Level 5~6: `[123] [134] [134] [678] [910]`
- 선택 주제 3개 + 돌발 후보 2개 배치
- 돌발 슬롯 랜덤화
- `ComboPattern.order` 제거
- 지원 주제/주제 그룹 `TopicCatalog` 공통화
- DB에 `QuestionSet`이 있는 주제만 후보로 사용
- 후보 부족 시 같은 주제를 반복하지 않고 모의고사 시작 차단

### Done (추가)

- 돌발 전용 풀 분리 — `TopicCatalog.surpriseTopics()` 23개 (5그룹) — 2026-06-12
- 돌발 주제 QuestionSet DataInitializer V1(10개)/V2(9개)/V3(4개) = 23개 삽입 — 2026-06-12
- `MockExamService` 배경설문 fallback 완전 제거, 돌발 전용 풀 사용 — 2026-06-12

### Next

- 기존 `Combo` 엔티티/전략 계열 제거 여부 결정
- `QuestionSet`이 `TYPE_1~TYPE_10`을 모두 갖는지 관리자 저장 시점 또는 테스트에서 검증
- 잘못된 topic/difficulty URL 파라미터 예외 처리

---

## Feedback Scoring & Analytics

### 배경

현재 `FeedbackResult`의 vocabulary, grammar, fluency 등 모든 항목이 자유 텍스트로 저장된다.
집계/분석이 불가능한 구조라 취약 유형 추천, 학습 이력 시각화를 할 수 없다.

### 계획

**1. 프롬프트 개선 — 점수 필드 추가**

LLM에게 텍스트 평가와 함께 1~5 정수 점수를 요청한다.

```json
{
  "vocabulary": "평가 설명",
  "vocabularyScore": 3,
  "grammar": "평가 설명",
  "grammarScore": 4,
  "fluency": "평가 설명",
  "fluencyScore": 2,
  "content": "평가 설명",
  "contentScore": 3,
  "mainPoint": "평가 설명",
  "mainPointScore": 3,
  "overall": "평가 설명",
  "overallGrade": "IM2"
}
```

**2. LLM 출력 결정론적 제어**

LLM 점수는 본질적으로 확률적이지만 다음 세 가지를 조합해 실용적으로 가둔다.

- `temperature: 0` — 같은 입력에 일관된 출력
- JSON Schema 강제 — integer 타입 + minimum/maximum 범위 선언
- 앱 레벨 클램핑 — 파싱 시 범위 벗어난 값 강제 보정 (`Math.clamp(score, 1, 5)`)

**3. FeedbackResult 스키마 변경**

`vocabularyScore`, `grammarScore`, `fluencyScore`, `contentScore`, `mainPointScore` (INT),
`overallGrade` (VARCHAR) 컬럼 추가.

**4. 취약 유형 분석 쿼리 예시**

```sql
SELECT questionType, AVG(grammarScore), AVG(fluencyScore)
FROM feedback_result
WHERE member_id = ?
GROUP BY questionType
ORDER BY AVG(grammarScore) ASC;
```

→ "TYPE_3 문제에서 문법 점수 낮음 → C1/C2 패턴 집중 추천"

### Done (추가)

- 프롬프트 + temperature 수정 (한국어 전용, temperature:0) — 2026-06-11
- JSON Schema 기반 structured output (Groq) — 2026-06-11
- FeedbackResult 스키마 마이그레이션 (6개 score 컬럼) — 2026-06-11
- FeedbackDTO 점수 필드 추가 — 2026-06-11

### Done (추가)

- LLM 응답 품질 검증 완료 (score 필드 정상 동작, 주제 관련성 채점 검증) — 2026-06-12
- 학습분석 탭 (/analytics) + 사이드바 탭 추가 — 2026-06-12
- 학습분석 UI 개선 (2컬럼 레이아웃, 동점 복수 강조, 미연습 유형 전체 표시, 타입 레이블 병기) — 2026-06-12
- 스터디 게시판 비활성화 (`@Profile("dev")` 3개 컨트롤러 + 사이드바 링크 제거) — 2026-06-12

### Done (추가)

- 유형별 연습 모드 (`/practice/type?type=TYPE_N`, `PracticeTypeController`) — 문서에 "미구현"으로 잘못 남아있었으나 실제로는 이미 구현·동작 확인함, 문서만 뒤늦게 반영 (2026-07-15)

### Next

(없음 — 아래 "유형별 연습 모드" 섹션도 참고, 완료됨)

---

## Learning Analytics & Recommendation

### Done

- 학습분석 탭: 항목별 평균 점수, 주제별 현황, 문제 유형별 점수 — 2026-06-12
- UI 개선: 2컬럼 레이아웃, 동점 복수 강조(weakestKeys), 미연습 유형 전체 표시, 레이블 병기 — 2026-06-12
- 유형별 연습 엔드포인트(`/practice/type`) → 학습분석/오늘 할 일 "연습" 버튼 연결 확인 (2026-07-15)

---

## 학습관리 재설계 — A(현황판)/B(오늘 할 일)/C(설정) 재배치

### 배경

기존에 "학습관리"를 4번째 화면(축)으로 만들려다, 약점 노출이 시험일정/학습분석/코칭에 이미 3중으로 흩어져 있어 보류됨. 기능 출처가 아니라 정보 종류로 재배치하는 방향으로 재설계 — 상세 설계 결정은 커밋 히스토리의 계획 문서 참고.

### Done

- `TodayController` (`GET /today`, `POST /today/task-done`) 신규 — B(오늘 할 일) 화면 — 2026-07-15
- `FeedbackResult.attemptId` 컬럼 추가 + `saveFeedbackResults()`에서 채움 → `COUNT(DISTINCT attemptId)`로 오늘 완료 콤보 개수 정확히 집계(답변 개수 아님)
- `CoachingReport.thisWeekTaskDone` 필드 추가 — 이번 주 과제 자기신고 체크박스, 새 리포트 생성 시 기본 false로 자동 리셋
- 회피 감지 2단계: D-day 구간별 임계값(D-4=2일/D-7=3일/D-14=5일/그 이상=7일 캡)로 방치된 유형 감지 → 그 중 약점 유형(상위 3)이면 우선순위 높게 표시
- `CoachingService.parseReport()` — `CoachingController`의 private 메서드를 public으로 승격, 코칭 리포트 JSON 파싱 로직 중복 제거
- `CoachingService.buildTeaser()` — 홈/`/analytics`/`/today` 공통 코칭 티저 문구 생성(리포트 있음/조건 충족/미충족 3가지 상태)
- 홈(`/`)에 B 요약 위젯 + 코칭 티저 위젯 2개 추가 (기존 보조카드 grid 아래, 새 nav 탭은 추가 안 함)
- `/analytics`(A)에 코칭 티저 카드 추가

### Next

- [ ] `/today` 회피 감지에 "얼마나 오래 미연습했는지"뿐 아니라 완전 미연습 유형도 포함할지 결정 (현재는 최소 1회 연습한 유형만 대상)

---

## 개별 연습 기록 조회 (History)

### 배경

과거에 제출한 개별 답변 피드백을 다시 볼 방법이 앱에 없었음 — `PracticeFeedbackController`는 세션 데이터에만 의존해서 제출 직후 결과 화면을 벗어나면 DB엔 남아있어도 다시 못 봄. "기록" 탭(`/analytics`, A)도 집계 통계만 있었지 개별 기록은 없었음.

### Done

- `HistoryController` (`GET /analytics/history` 목록, `GET /analytics/history/{id}` 상세) 신규 — 2026-07-15
- `FeedbackResultRepository.findByIdAndMemberId` 추가 (소유권 체크, `CoachingReportRepository`와 동일 패턴)
- `ExamPlanService.typeLabel()` public으로 승격해 유형 라벨 재사용
- `/analytics`(A)에 최근 기록 미리보기(5개) + "전체 보기" 링크 추가
- 홈(`/`)에 5번째 카드 "최근 기록" 추가

### Next

- [ ] 페이지네이션 (지금은 최근 20개만, UI 없음)

---

## 채점 항목별 집중 연습 모드 (Focus Mode)

### 배경

현재 연습은 항상 전체 5개 항목(어휘/문법/메인포인트/유창성/내용)을 동시에 평가한다.
하지만 OPIc 등급 향상을 위해 특정 항목에 집중하는 연습이 더 효과적인 경우가 있다.

### 항목별 의미 (OPIc 등급 관점)
- **mainPoint + content**: 핵심 포인트를 정했는지, 그걸 중심으로 전체 흐름을 끌고 가는지 — IL→IM 핵심
- **vocabulary**: 형용사, 감정 표현, 비유, 묘사 등 표현의 풍부함 — IM→IH 핵심
- **fluency**: 끊기지 않고 문단 단위로 이어가는 능력 — 별도 모드 효과 의문, 보류
- **grammar**: 시제 일관성, 문장 구조 다양성 — 나중에 검토

### 계획
1. **메인포인트 집중 모드** (우선순위 1)
   - 프롬프트: mainPoint + content만 평가, 나머지 생략
   - 피드백: "포인트가 있었나요? 전체 흐름이 일관적였나요?"
   - UI: 답변 후 mainPoint/content 2개 점수 + 코칭 메시지만 표시

2. **어휘/표현 집중 모드** (우선순위 2)
   - 프롬프트: vocabulary만 평가 — 형용사, 감정, 비유 사용 여부 중심
   - 피드백: "이 문장을 더 풍부하게 바꾸면?" 제안 포함
   - UI: vocabularyScore + 개선 예시 표시

3. **문법 모드** (보류 — 나중에 결정)

### 진입점
- 메인 홈화면: "집중 연습" 섹션 (유형별 연습과 함께)
- 학습분석 화면: 약점 항목 옆 "집중 연습하기" 버튼

---

## 유형별 연습 모드 (Type-Based Practice)

**구현 완료** (`PracticeTypeController`, `/practice/type?type=TYPE_N`) — 아래는 당시 계획 기록.

### 배경

OPIc에서 유형(묘사/경험/롤플레이 등)에 익숙해지면 주제가 바뀌어도 답변 패턴 재활용 가능.
현재 연습은 주제 기준이고 유형을 선택할 수 없다.

문제 유형 정의는 `DOMAIN.md` 참고.

### 계획
- 특정 유형의 문제만 뽑아 연습하는 엔드포인트
- 주제는 랜덤 (유형 고정, 주제 랜덤)
- 피드백은 기존 전체 평가 사용

### 진입점
- 메인 홈화면: "유형별 연습" 섹션
- 학습분석 화면: 문제 유형별 점수 옆 "연습하기" 버튼
