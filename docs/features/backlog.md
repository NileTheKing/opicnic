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
POST /api/practice-attempts/answers

실패 문항 재제출:
POST /api/practice-attempts/answers/retry

결과 확정:
POST /api/practice-attempts/answers/finalize

결과 화면:
GET /practice/feedback/result
```

현재 구조는 `시작/결과 화면 = Thymeleaf`, `제출/재시도/finalize = API`인 중간 단계다.

### Next

- `restoreQuestionsForIndexes` 캐싱 — 500 VU 부하테스트에서 발견된 병목. 매 제출마다 DB 조회 → ConcurrentHashMap 캐싱으로 해소 예정
- API 응답 DTO 정리
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

### Next

- [ ] 유형별 연습 모드 (`/practice/type?type=TYPE_N`, 홈 카드 섹션 + 학습분석 "연습" 버튼)
- [ ] 복습 추천 로직 (마지막 연습일 + 점수 기반 간격 조정)

---

## Learning Analytics & Recommendation

### Done

- 학습분석 탭: 항목별 평균 점수, 주제별 현황, 문제 유형별 점수 — 2026-06-12
- UI 개선: 2컬럼 레이아웃, 동점 복수 강조(weakestKeys), 미연습 유형 전체 표시, 레이블 병기 — 2026-06-12

### Next

- [ ] 유형별 연습 엔드포인트 구현 후 학습분석 → "연습" 버튼 연결
- [ ] 복습 추천 로직 (마지막 연습일 + 점수 기반)

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

### 배경

OPIc에서 유형(묘사/경험/롤플레이 등)에 익숙해지면 주제가 바뀌어도 답변 패턴 재활용 가능.
현재 연습은 주제 기준이고 유형을 선택할 수 없다.

### OPIc 문제 유형 정의
| 코드 | 유형명 | 핵심 스킬 |
|---|---|---|
| TYPE_1 | 유형 1 · 현재 상태 묘사 | 현재시제, 장소/물건/사람 묘사 |
| TYPE_2 | 유형 2 · 루틴/습관 | 현재시제, 빈도 부사, 일상 서술 |
| TYPE_3 | 유형 3 · 최근/최초 경험 | 과거시제, 시간순 서술 (최근 경험 + 처음 해본 경험 둘 다 포함) |
| TYPE_4 | 유형 4 · 기억에 남는 경험 | 과거시제, 감정 표현, 이유 설명 |
| TYPE_5 | 롤플레이 · 도입 | 상황 설정, 정중한 요청 |
| TYPE_6 | 롤플레이 · 전화/질문 | 3~4개 질문 구성 |
| TYPE_7 | 롤플레이 · 문제 해결 | 대안 2~3개 제시 |
| TYPE_8 | 롤플레이 · 비슷한 경험 | 과거시제, 롤플레이와 연결 |
| TYPE_9 | 유형 9 · 과거·현재 비교 | 시제 전환, 비교 표현 |
| TYPE_10 | 유형 10 · 사회 이슈 | 논리적 전개, 의견 표현 |

### 계획
- 특정 유형의 문제만 뽑아 연습하는 엔드포인트
- 주제는 랜덤 (유형 고정, 주제 랜덤)
- 피드백은 기존 전체 평가 사용

### 진입점
- 메인 홈화면: "유형별 연습" 섹션
- 학습분석 화면: 문제 유형별 점수 옆 "연습하기" 버튼
