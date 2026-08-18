# 제품 계약 감사 및 후속 작업 인계서

> 기준 커밋: `b1168e8` (`main`)  
> 감사일: 2026-08-13  
> 범위: 가입 → 온보딩 → 연습 → 피드백 → 기록 → 코칭 → 시험 계획 → 오늘 할 일  
> 상태: **발견만 완료. 아래 항목은 별도 표시가 없으면 아직 수정하지 않았다.**  
> 대상 독자: 항목을 실제 코드 변경과 회귀 테스트로 이어받을 후속 에이전트
>
> **2026-08-13 후속 수정**: 권장 구현 순서 1단계(PC-01, PC-02, PC-03, PC-04, PC-05, PC-10, PC-11, PC-21) 완료.
> PC-04/PC-11은 1차 수정에서 백엔드만 막고 화면에 실패 이유를 안 보여줘 "조용히 안 되는" 상태였던 걸 재검토 후 화면(에러 배너, 미지원 콤보 숨김)까지 마저 수정.
> 각 코드 변경에 회귀 테스트 추가(`src/test/java/.../controller`, `.../service`), 상세는 [`docs/CHANGELOG.md`](CHANGELOG.md) 참고. 나머지 항목(PC-06~09, PC-12~20, PC-22~24)은 미착수.

## 0. 이 문서를 사용하는 방법

이 문서는 제품 아이디어 요약이나 사용자용 보고서가 아니다. 후속 에이전트가 다시 전체 코드베이스를 감사하지 않고도 개별 작업을 시작할 수 있도록 다음 정보를 남긴다.

- 화면이 사용자에게 약속하는 동작
- 현재 코드가 실제로 하는 동작
- 관련 클래스·메서드·템플릿과 데이터 필드
- 최소 재현 시나리오
- 수정할 때 지켜야 할 경계
- 작업 완료를 판정할 자동 테스트/수동 확인 조건
- 먼저 제품 결정을 받아야 하는 부분

라인 번호는 기준 커밋의 위치 힌트다. 구현 시에는 파일명뿐 아니라 기재된 클래스·메서드·문구를 `rg`로 다시 찾아야 한다.

### 상태 표기

| 표기 | 의미 |
|---|---|
| `READY` | 기대 동작이 코드·화면·도메인 문서에서 충분히 명확해 바로 수정 가능 |
| `DECISION` | 문제가 있다는 사실은 확정이지만, 목표 동작을 사람이 먼저 결정해야 함 |
| `MIXED` | 안전한 최소 수정은 가능하지만 완전한 해결에는 제품 결정 또는 데이터 모델 변경이 필요 |

### 공통 구현 원칙

1. 항목 하나를 맡으면 먼저 현재 오동작을 재현하는 테스트를 작성한다.
2. 서로 다른 ID를 한 번에 대규모 리팩터링하지 않는다. 아래 의존성이 명시된 경우만 묶는다.
3. 화면 문구만 맞추는 임시 수정과 실제 계약을 고치는 수정을 구분한다.
4. 숫자·통계 관련 변경은 기존 데이터의 의미와 마이그레이션 필요 여부를 기록한다.
5. 완료 후 `docs/CHANGELOG.md`, `docs/backlog.md`, `PROJECT.md` 중 `AGENTS.md`의 Documentation Checkpoint에 해당하는 문서를 갱신한다.

### 핵심 용어

- **문항(question result)**: `FeedbackResult` 한 행. 현재 대부분의 집계가 이 단위를 사용한다.
- **attempt**: 사용자가 한 번 연습 화면에 진입해 제출하는 단위. `attemptId`로 식별한다.
- **콤보**: C1~C5 패턴에 따라 2~3개 문항을 묶은 연습 단위.
- **모의고사**: 자기소개 1문항과 콤보 슬롯 5개로 구성된 총 15문항 attempt.
- **콤보 슬롯**: 모의고사 안의 C1~C5 패턴 하나. 현재 영속 결과에는 슬롯 식별자가 없다.

## 1. 우선순위와 작업 준비도

| ID | 심각도 | 준비도 | 요약 | 직접 영향 |
|---|---:|---|---|---|
| PC-01 | P1 | ✅ 완료 (08-13) | 모의고사 자기소개가 정상 답변일수록 실패 | 핵심 15문항 플로우 완료 불가 |
| PC-02 | P1 | ✅ 완료 (08-13) | 돌발 연습이 돌발 전용 풀이 아님 | 버튼과 실제 출제가 다름 |
| PC-03 | P1 | ✅ 완료 (08-13) | 유형별 연습이 내 주제를 사용하지 않음 | 선택하지 않은 일반·돌발 주제 출제 |
| PC-04 | P1 | ✅ 완료 (08-13) | 난이도에 없는 C4/C5가 다른 콤보로 조용히 대체됨 | 사용자가 고른 훈련과 다른 문제 출제 |
| PC-05 | P1 | ✅ 완료 (08-13) | 마이페이지 저장 시 거주 주제가 사라짐 | 랜덤·모의고사 후보가 조용히 변경 |
| PC-06 | P1 | MIXED | 목표 등급·난이도를 나중에 바꿀 수 있다는 약속이 거짓 | 온보딩 설정이 사실상 고정 |
| PC-07 | P1 | MIXED | 코칭의 현재/목표 등급과 평균이 전 사용자 공통 하드코딩 | 개인화 화면의 핵심 수치가 허위 표시 |
| PC-08 | P1 | DECISION | 오늘의 “콤보 완료”가 모든 종류의 attempt를 셈 | 유형 1문항과 모의고사 15문항이 같은 1회 |
| PC-09 | P1 | DECISION | 시험 계획이 진단·목표 등급을 사용하지 않음 | 사용자 실력과 목표가 달라도 같은 계획 |
| PC-10 | P1 | ✅ 완료 (08-13) | 현재 문항 녹음은 이탈 경고 없이 사라짐 | 긴 연습 중 사용자 입력 유실 |
| PC-11 | P2 | ✅ 완료 (08-13) | 마이페이지의 12개 최소 규칙이 저장을 막지 않음 | 온보딩 정책과 설정 화면이 충돌 |
| PC-12 | P2 | MIXED | 방금 본 결과 페이지가 새로고침 불가능한 일회성 화면 | 저장 실패처럼 보이고 결과 공유/복구 불가 |
| PC-13 | P2 | READY | “전체 보기”가 최근 20문항만 표시 | 21번째 이전 기록에 접근 불가 |
| PC-14 | P2 | DECISION | 콤보별 `N회`가 콤보 횟수가 아니라 문항 행 수 | C1 1회가 3회로 표시 |
| PC-15 | P2 | DECISION | “이번 주 N회 연습”의 N은 과거 표본 수이고 만료가 없음 | 과거 12회가 미래 목표 12회로 오표시 |
| PC-16 | P2 | READY | 시험 당일을 이미 지난 시험으로 처리 | 당일 계획 0, D-0과 과거 시험 구분 불가 |
| PC-17 | P2 | READY | 기록이 없어도 “기록 기반 약한 순”이라고 표시 | enum 기본 순서를 개인 추천처럼 설명 |
| PC-18 | P2 | DECISION | 개별 피드백과 시험 추정의 등급 공식이 다름 | 같은 사용자가 화면마다 다른 등급 |
| PC-19 | P2 | MIXED | 모의고사 콤보 슬롯 정보가 영구 손실 | 모의고사가 콤보 분석에 반영될 수 없음 |
| PC-20 | P2 | READY | 알림 설정은 저장만 되고 실제 알림 기능이 없음 | 사용자가 켜도 아무 일도 일어나지 않음 |
| PC-21 | P2 | ✅ 완료 (08-13) | 두 탭에서 온보딩 완료 시 두 번째 요청이 500 | 정상 사용자 행동이 서버 오류로 노출 |
| PC-22 | P2 | DECISION | AI 등급·점수에 전문가 기준 검증과 버전 정보가 없음 | 점수 변화를 실력 변화로 신뢰하기 어려움 |
| PC-23 | P2 | DECISION | 음성 외부 전송·보관·삭제에 대한 제품 설명과 제어가 없음 | 신뢰·개인정보 기대를 설정할 수 없음 |
| PC-24 | P3 | READY | 코칭 진입 조건의 `문항/회`와 설정값 문구가 불일치 | 같은 조건을 화면마다 다르게 설명 |

심각도는 제품 사용성과 신뢰 기준이다. 보안·운영 심각도와 상세 근거는 [`codebase-risk-audit-2026-08-13.md`](codebase-risk-audit-2026-08-13.md)를 함께 본다.

## 2. 가입·온보딩·설정

### PC-05 [P1, READY]. 마이페이지 저장 시 거주 주제가 사라진다

> **✅ 완료 (2026-08-13)**: `MyPageController.updateSurvey()`가 저장 시 거주 주제 두 개(`LIVING_WITH_FAMILY`/`LIVING_ALONE`)를 지운 뒤 현재 `residenceType`에 맞는 것 하나만 다시 추가하도록 수정. 테스트: `MyPageControllerUpdateSurveyTest`.

**제품 계약**

- 온보딩과 마이페이지는 거주 형태가 실제 출제 범위에 영향을 준다고 설명한다.
- 사용자가 설정을 열어 아무것도 바꾸지 않고 저장해도 기존 선택의 의미는 보존되어야 한다.
- 거주 형태를 바꾸면 `LIVING_WITH_FAMILY`와 `LIVING_ALONE` 중 맞는 주제가 함께 갱신되어야 한다.

**현재 동작**

1. 온보딩 완료 시 `OnboardingController.completeOnboarding()`이 거주 형태에 대응하는 주제를 `selectedTopics`에 자동 추가한다.
2. 마이페이지의 `buildTopicGroups()`에는 거주 주제가 없다.
3. `MyPageController.updateSurvey()`는 기존 `selectedTopics`를 전부 지우고 화면의 체크박스 값만 다시 넣는다.
4. 따라서 마이페이지에서 아무 변경 없이 저장해도 거주 주제가 삭제된다. 거주 형태를 바꿔도 새 거주 주제가 추가되지 않는다.

**근거**

- `src/main/java/com/opicnic/opicnic/controller/OnboardingController.java:140-146`
  - 선택 주제를 넣은 뒤 거주 주제를 자동 추가한다.
- `src/main/java/com/opicnic/opicnic/controller/MyPageController.java:85-90`
  - 목록 전체를 `clear()`하고 전달된 주제만 추가한다.
- `MyPageController.buildTopicGroups():121-138`
  - 여가/취미/운동/여행만 있고 거주 주제가 없다.
- `src/main/resources/templates/mypage/mypage.html:119-142`
  - “거주 형태가 세트 수에 영향을 준다”고 설명한다.

**최소 재현**

1. 신규 사용자로 `WITH_FAMILY`를 선택해 온보딩을 완료한다.
2. DB 또는 repository 테스트에서 `selectedTopics`에 `LIVING_WITH_FAMILY`가 있는지 확인한다.
3. 마이페이지에서 값을 바꾸지 않고 `/mypage/survey`를 제출한다.
4. `LIVING_WITH_FAMILY`가 사라지는지 확인한다.
5. `ALONE`으로 바꿔 제출했을 때 `LIVING_ALONE`도 추가되지 않는지 확인한다.

**수정 경계**

- 거주 주제를 일반 선택 주제와 같은 컬렉션에 계속 둘지, `residenceType`에서 출제 시점에 파생할지 결정한다.
- 최소 수정은 저장 시 두 거주 주제를 제거하고 현재 `residenceType`에 맞는 하나를 다시 추가하는 것이다.
- 중복·잘못된 두 거주 주제가 이미 저장된 데이터를 어떻게 정리할지도 검토한다.

**완료 조건**

- 아무 변경 없이 마이페이지 저장 후 주제 집합이 동일하다.
- 거주 형태 변경 후 이전 거주 주제는 없고 새 거주 주제만 정확히 하나 있다.
- 랜덤 연습과 모의고사 후보에서 현재 거주 주제가 유지된다.
- controller 또는 service 테스트가 두 거주 형태를 모두 검증한다.

---

### PC-06 [P1, MIXED]. 목표 등급·연습 난이도를 나중에 변경할 수 있다는 진입 경로가 막혀 있다

**제품 계약**

- 온보딩 1단계는 목표 등급과 연습 난이도를 설정한다.
- 화면 하단은 “나중에 마이페이지에서 언제든 변경할 수 있어요”라고 안내한다.
- 주제 탐색 화면의 현재 난이도 옆 “변경” 링크도 `/mypage`로 이동한다.

**현재 동작**

- `SurveyProfile`에는 `targetGrade`와 `preferredDifficulty`가 저장된다.
- 그러나 마이페이지의 조회 모델과 수정 폼에는 거주 형태와 선택 주제만 있다.
- `MyPageController.updateSurvey()`도 목표 등급·난이도를 받거나 저장하지 않는다.
- 사용자는 온보딩 후 해당 값을 바꿀 공식 UI/API가 없다.

**근거**

- `src/main/resources/templates/onboarding/onboarding.html:148-203,211`
- `src/main/resources/templates/practice/topics.html:34-39`
- `src/main/resources/templates/mypage/mypage.html:90-176`
- `src/main/java/com/opicnic/opicnic/controller/MyPageController.java:45-53,71-93`
- `src/main/java/com/opicnic/opicnic/domain/SurveyProfile.java:33-38`

**최소 재현**

1. 온보딩에서 목표 `IM2`, 난이도 `LEVEL_4`로 완료한다.
2. 마이페이지에 들어가 두 값을 변경할 컨트롤이 없는지 확인한다.
3. 주제 탐색의 난이도 “변경” 링크를 눌러도 변경 UI가 없는지 확인한다.

**수정 경계**

- 최소 제품 계약을 유지하려면 마이페이지에서 두 값을 편집하게 해야 한다.
- 기능을 제공하지 않을 결정이라면 온보딩과 주제 탐색의 문구/링크를 제거해야 한다.
- 목표 등급은 PC-07, PC-09, PD-02의 단일 source of truth 결정과 함께 다루는 편이 안전하다.

**완료 조건**

- 사용자가 현재 목표 등급과 난이도를 확인할 수 있다.
- 변경 저장 후 `SurveyProfile`과 다음 연습의 문제 패턴이 새 난이도를 사용한다.
- 허용하지 않는 등급·난이도 값은 서버에서 거부한다.
- 마이페이지·주제 탐색·코칭·시험 계획의 목표 등급 표기가 정해진 정책과 일치한다.

---

### PC-11 [P2, READY]. “12개 이상 필수”가 실제 저장 규칙이 아니다

> **✅ 완료 (2026-08-13)**: 신규 `SurveyTopicPolicy`(총 12개 + 그룹별 최소)를 온보딩·마이페이지 폼 제출과 toggle API가 공통으로 사용하도록 배선. 검증 실패 시 기존 설정은 보존되고 `?error=invalidTopics`로 리다이렉트, 마이페이지/온보딩 주제선택 화면에 실패 배너 표시. 테스트: `SurveyTopicPolicyTest`, `MyPageControllerUpdateSurveyTest`, `OnboardingControllerDuplicateSubmitTest`.
> **후속 수정 (2026-08-13, 재검토)**: 위 1차 수정에서 toggle API의 "주제 추가" 경로는 최소 개수 검증만 걸리고 **허용 목록(돌발 전용 주제 등) 검증이 빠져있던 것을 재검토로 발견** — 화면 밖에서 직접 API를 호출하면 돌발 전용 주제를 "내 주제"로 넣을 수 있었다. `SurveyTopicPolicy.isAllowedTopic()` 추가, `MyPageController.toggleTopic()` 추가 경로에도 적용. 그룹 정의가 `OnboardingController`/`MyPageController`/`SurveyTopicPolicy` 3곳에 동일 내용으로 중복돼 있는 것은 아직 미정리(단일 소스로 리팩터링 필요, 우선순위 낮음).

**제품 계약**

- 온보딩은 총 12개 이상과 그룹별 최소 개수를 만족해야 다음 단계가 활성화된다.
- 마이페이지도 “12개 이상 필수”, “12개 이상 선택해야 저장할 수 있어요”라고 표시한다.

**현재 동작**

- 마이페이지 JavaScript는 12개 미만일 때 경고만 보이고 submit을 disable하지 않는다.
- 서버는 `selectedTopics`가 없으면 전체 목록을 비우고, 0~11개도 그대로 저장한다.
- `/mypage/topics/toggle`은 최소 개수나 허용 주제 집합을 검사하지 않는다.

**근거**

- `src/main/resources/templates/mypage/mypage.html:141-168,252-269`
- `src/main/java/com/opicnic/opicnic/controller/MyPageController.java:71-92,96-118`
- 관련 기술 감사: `API-02`

**최소 재현**

- 마이페이지에서 모든 주제 체크를 해제하고 “설정 저장”을 누른다.
- 응답이 성공하고 DB가 빈 목록이 되는지 확인한다.
- toggle API로 12개 아래까지 제거 가능한지 확인한다.

**수정 경계**

- 총 12개뿐 아니라 온보딩의 그룹별 최소 규칙을 계속 유지할지 확인한다.
- `NO_EXERCISE`와 거주 주제를 12개 카운트에 포함하는지 명시해야 한다.
- UI 검사는 사용자 편의일 뿐이며 동일 규칙을 서버의 한 validator/service에서 재사용해야 한다.

**완료 조건**

- 폼 POST와 toggle API가 동일한 규칙을 적용한다.
- 잘못된 요청은 기존 설정을 보존하고 400 또는 명시적 오류 응답을 반환한다.
- 11개/12개, 그룹 최소 미달/충족, 중복 파라미터, 허용되지 않은 주제 테스트가 있다.

---

### PC-21 [P2, READY]. 온보딩을 두 탭에서 완료하면 두 번째 탭이 500이 된다

> **✅ 완료 (2026-08-13)**: `OnboardingController.completeOnboarding()`이 진입 시 기존 profile 존재 여부를 다시 확인해 있으면 홈으로 리다이렉트(순차 중복 방어), `save()`를 `DataIntegrityViolationException`으로 감싸 진짜 동시 요청도 500 대신 홈으로 수렴하도록 함. 테스트: `OnboardingControllerDuplicateSubmitTest`.

**현재 동작과 원인**

- 두 탭 모두 profile이 없을 때 온보딩 2단계까지 진입할 수 있다.
- 첫 탭 완료 후 두 번째 탭의 POST는 기존 profile을 다시 조회하지 않고 새 `SurveyProfile`을 insert한다.
- `survey_profile.member_id`는 unique이므로 두 번째 저장이 무결성 예외로 끝난다.

**근거**

- `OnboardingController.showTopics():84-98`
- `OnboardingController.completeOnboarding():116-149`
- `SurveyProfile.java:23-25`

**최소 재현**

- 신규 회원의 온보딩을 두 브라우저 탭에서 연다.
- 두 탭 모두 마지막 단계까지 이동한 뒤 차례로 제출한다.
- 두 번째 요청이 사용자 친화적 완료/리다이렉트가 아니라 500인지 확인한다.

**수정 경계**

- POST를 멱등하게 처리해 이미 완료됐다면 홈으로 보내거나, 기존 profile을 갱신하지 않고 성공으로 종료한다.
- unique constraint는 제거하지 않는다. 동시 요청의 최종 방어선으로 유지해야 한다.

**완료 조건**

- 순차 중복 제출과 실제 동시 제출 모두 500이 나지 않는다.
- 회원당 profile은 정확히 하나다.
- 두 요청의 값이 다를 때 어떤 값이 이기는지 테스트 또는 정책으로 고정한다.

## 3. 연습 선택과 출제

### PC-01 [P1, READY]. 모의고사 자기소개 문항은 정상 답변일수록 실패한다

> **✅ 완료 (2026-08-13)**: `FeedbackService`가 `questionType==null`(자기소개)이면 채점/태깅 LLM 호출 없이 완료 처리. 자기소개는 실제 시험에서도 채점 문항으로 취급되지 않으므로 `PracticeAttemptApiController.saveFeedbackResults()`가 DB 저장 자체에서 제외해 "총 문항 수"/"최근 기록"/"코칭 열람 조건" 등 문항 개수 기반 통계에도 섞이지 않는다. `typeLabel(null)` null-safe 처리. 테스트: `FeedbackServiceSelfIntroTest`, `ExamPlanServiceTypeLabelTest`, `PracticeAttemptApiControllerFinalizeTest`.

**제품 계약**

- 홈과 시험 준비 화면은 “실전처럼 15문항”, “실제 시험과 동일한 구성 · 15문항”을 약속한다.
- 자기소개를 포함한 모든 문항을 정상적으로 답하면 결과 확정 단계로 이동해야 한다.

**현재 동작**

- 자기소개는 DB에 없는 고정 문항이라 `QuestionDto.questionType=null`이다.
- 5단어 이상 답변이면 채점 후 태깅 단계에서 `question.getQuestionType().name()`을 호출해 NPE가 난다.
- 실패 문항은 attempt 완료 map에 들어가지 않아 15개가 모두 모일 수 없다.
- 5단어 미만 답변만 무응답 조기 반환을 타서 우연히 통과할 수 있다.

**근거**

- `src/main/java/com/opicnic/opicnic/service/MockExamService.java:156-162`
- `src/main/java/com/opicnic/opicnic/service/attempt/PracticeAttemptService.java:88-94`
- `src/main/java/com/opicnic/opicnic/service/FeedbackService.java:82-98,151-167`
- `src/main/java/com/opicnic/opicnic/controller/PracticeAttemptApiController.java:77-80,128-137`
- 기술 감사 `CORE-01`

**최소 재현**

- 모의고사 자기소개 index 0에 5단어 이상의 유효 audio/STT fixture를 제출한다.
- 반환 결과의 `failedIndexes`에 0이 포함되고 finalize가 거부되는지 확인한다.

**수정 경계**

- 자기소개에 별도 `QuestionType`을 추가할지, 채점/태깅 제외 문항으로 명시할지 결정한다.
- 단순 null guard만 넣으면 기록·등급·코칭에서 자기소개가 어떤 의미인지 다시 모호해질 수 있다.
- 최소 안전 수정은 자기소개 전용 처리 정책을 하나 정해 피드백, 태깅, 영속, 기록 렌더링 전체에 동일하게 적용하는 것이다.

**완료 조건**

- 5단어 이상 자기소개와 나머지 14문항을 제출해 finalize가 성공한다.
- 자기소개가 기록 상세·분석·코칭에서 정해진 정책대로 포함 또는 제외된다.
- `questionType=null`로 인해 `.name()`이나 type label 렌더링이 실패하지 않는다.

---

### PC-02 [P1, READY]. “돌발로 하기”가 돌발 전용 풀을 사용하지 않는다

> **✅ 완료 (2026-08-13)**: `HomeController.surprisePractice()`가 `topicCatalog.practiceTopics()` 대신 `surpriseTopics()`만 조회하도록 수정. 테스트: `HomeControllerSurprisePracticeTest`.

**제품 계약**

- `DOMAIN.md`는 돌발 주제를 배경설문 주제와 완전히 별개인 `TopicCatalog.surpriseTopics()` 23개 풀로 정의한다.
- 홈 버튼 문구는 “돌발로 하기”다.

**현재 동작**

- `HomeController.surprisePractice()`는 `topicCatalog.practiceTopics()`를 사용한다.
- `practiceTopics()`는 일반 지원 주제와 돌발 주제를 합친 목록이다.
- 따라서 돌발 버튼에서 일반 배경설문 주제가 나올 수 있다.

**근거**

- `DOMAIN.md:44-51`
- `src/main/java/com/opicnic/opicnic/controller/HomeController.java:117-130`
- `src/main/java/com/opicnic/opicnic/service/TopicCatalog.java:86-92,110-115`
- 기술 감사 `DOMAIN-01`

**최소 재현**

- repository가 일반 주제와 돌발 주제를 모두 반환하도록 고정한 controller 테스트를 작성한다.
- 반복 또는 deterministic `Random`으로 일반 주제가 선택될 수 있음을 재현한다.

**완료 조건**

- 후보는 `surpriseTopics()`와 실제 question set 존재 목록의 교집합이다.
- 일반 주제가 단 하나도 후보에 포함되지 않는 테스트가 있다.
- 돌발 풀이 비어 있을 때 사용자에게 명시적 빈 상태/오류가 제공된다.

---

### PC-03 [P1, READY]. 유형별 연습의 “내 주제 중 랜덤” 약속이 구현되어 있지 않다

> **✅ 완료 (2026-08-13)**: `PracticeTypeController`가 전체 지원 주제 대신 `SurveyProfile.selectedTopics`(NO_EXERCISE 제외)와 실제 존재하는 question set의 교집합에서만 주제를 뽑도록 수정. 선택 주제가 없거나 후보가 없으면 `invalidPractice=true`로 명시적 리다이렉트. 테스트: `PracticeTypeControllerTest`.

**제품 계약**

- 집중 연습의 유형별 탭은 “문제 유형 선택 · 내 주제 중 랜덤으로 1문항 연습”이라고 설명한다.

**현재 동작**

- `PracticeTypeController`는 사용자 `SurveyProfile`을 조회하지 않는다.
- 일반+돌발 전체 `topicCatalog.practiceTopics()` 중 question set이 있는 주제를 뽑는다.
- 선택하지 않은 주제와 돌발 주제도 출제될 수 있다.
- 생성된 attempt mode도 별도 TYPE이 아니라 `PracticeMode.COMBO`다. 이 문제는 PC-08과 연결된다.

**근거**

- `src/main/resources/templates/practice/focus.html:140-143`
- `src/main/java/com/opicnic/opicnic/controller/PracticeTypeController.java:38-58`

**최소 재현**

- profile에 일반 주제 하나만 저장하고 전체 question set에는 다른 일반/돌발 주제도 있게 한다.
- 유형 연습을 시작했을 때 선택하지 않은 주제가 후보가 되는지 controller/service 테스트로 확인한다.

**수정 경계**

- 문구대로라면 profile 선택 주제와 실제 question set의 교집합에서만 뽑아야 한다.
- 내 주제가 비어 있거나 선택한 주제에 요청 유형 문항이 없을 때 fallback 정책을 명시해야 한다. 다른 주제로 조용히 대체하지 않는다.
- `PracticeMode.TYPE` 추가 여부는 PC-08/PC-19의 데이터 모델 결정과 함께 처리한다.

**완료 조건**

- 선택 주제 밖의 일반·돌발 주제가 출제되지 않는다.
- 후보 없음 상태를 명시적으로 처리한다.
- 선택한 `QuestionType`이 실제 문항과 일치한다.

---

### PC-04 [P1, MIXED]. 난이도에 없는 C4/C5 선택이 다른 콤보로 조용히 대체된다

> **✅ 완료 (2026-08-13)**: `ComboPracticeService.buildResult()`가 지원 안 되는 category 요청 시 다른 category로 대체하지 않고 `IllegalStateException`을 던지도록 수정(선택지 B: 명시적 오류). 여기서 멈추면 백엔드만 막고 화면은 설명 없이 홈으로 튕기는 상태였는데, 후속 수정으로 `/practice/focus`(집중 연습 콤보별 탭)가 `OpicComboPatternProvider` 기준으로 현재 난이도에 없는 버튼(C4 또는 C5)을 아예 숨기고 안내 문구를 표시하도록, `/exam`(시험 계획 약한 콤보 추천)도 현재 난이도에서 실제 시작 가능한 category만 추천하도록 수정. 테스트: `ComboPracticeServiceTest`, `PracticeFocusControllerTest`, `ExamControllerWeakComboFilterTest`.

**제품 계약**

- 집중 연습과 시험 계획은 C1~C5를 구체적인 훈련 패턴으로 표시하고 각 카드에서 해당 category 연습으로 이동한다.

**현재 동작**

- 레벨 3~4 패턴에는 C5가 없다.
- 레벨 5~6 패턴에는 C4가 없다.
- `ComboPracticeService.buildResult()`는 요청 category 필터 결과가 비면 오류를 내지 않고 원래 전체 패턴 목록을 유지한다.
- 사용자가 C5를 눌렀는데 C1~C4 중 임의 패턴이 나오는 식의 조용한 대체가 발생한다.

**근거**

- `src/main/java/com/opicnic/opicnic/service/OpicComboPatternProvider.java:13-30`
- `src/main/java/com/opicnic/opicnic/service/ComboPracticeService.java:31-41`
- `src/main/resources/templates/practice/focus.html:78-137`
- `src/main/resources/templates/exam/prep.html:74-100`

**최소 재현**

- `LEVEL_4 + category=C5`와 `LEVEL_5 + category=C4`를 서비스 테스트로 호출한다.
- 반환 category가 요청값과 다르면서 성공하는 현재 동작을 고정한다.

**선행 결정**

- 선택 불가능한 category를 화면에서 숨길지,
- 해당 category용 패턴을 모든 난이도에 제공할지,
- 사용자에게 “현재 난이도에서는 지원하지 않음”을 안내할지 정해야 한다.

어떤 결정을 하더라도 다른 category로 조용히 대체하는 동작은 제거할 수 있다.

**완료 조건**

- 성공 응답의 category는 요청 category와 항상 같다.
- 지원하지 않는 조합은 UI에서 선택 불가이거나 명시적 오류/안내로 끝난다.
- 시험 계획 추천도 현재 난이도에서 실제 시작 가능한 category만 링크한다.

---

### PC-10 [P1, READY]. 현재 문항 녹음은 이탈 경고 대상에서 빠진다

> **✅ 완료 (2026-08-13)**: `practice/question.html`의 `warnBeforeLeavingWithRecordings()`가 `recordings[]`뿐 아니라 아직 커밋 전인 현재 문항의 `recordedAudioBlob`도 확인하도록 수정.

**현재 동작**

- 녹음 완료 직후 blob은 `recordedAudioBlob`에 있다.
- `recordings[currentQuestionIndex]`에는 “다음” 또는 최종 제출을 누를 때만 들어간다.
- `beforeunload` 경고는 `recordings.some(Boolean)`만 확인한다.
- 첫 문항 또는 유형 1문항에서 녹음 후 다음/제출 전 새로고침하면 경고 없이 녹음이 사라진다.

**근거**

- `src/main/resources/templates/practice/question.html:164-171,228-239,297-302,304-338`

**최소 재현**

1. 유형별 1문항 연습에 진입한다.
2. 녹음을 시작하고 정지한다.
3. 제출 버튼을 누르지 않고 새로고침한다.
4. 이탈 경고가 발생하지 않고 녹음이 사라지는지 확인한다.

**수정 경계**

- 최소 수정은 `recordedAudioBlob` 또는 현재 녹음 상태도 이탈 조건에 포함하는 것이다.
- 브라우저 저장소에 audio를 복구 가능하게 보존하는 것은 별도 기능이며 이 항목의 필수 범위가 아니다.
- media stream track을 정지하지 않는 문제도 별도 점검할 수 있지만 이 ID의 완료 조건에 억지로 포함하지 않는다.

**완료 조건**

- 현재 문항 녹음만 존재해도 새로고침/탭 닫기/페이지 이동에 경고가 뜬다.
- 제출 완료 후에는 경고가 뜨지 않는다.
- 녹음 전에는 불필요한 경고가 뜨지 않는다.

## 4. 피드백·기록·복구

### PC-12 [P2, MIXED]. 연습 결과 화면이 일회성 세션 뷰다

**제품 계약**

- URL과 화면 제목은 일반적인 “연습 결과” 페이지처럼 보인다.
- finalize는 결과를 DB에 영구 저장한다.
- 사용자는 방금 본 결과를 새로고침해도 같은 내용을 기대한다.

**현재 동작**

- finalize가 DTO 목록을 `HttpSession.feedbackResults`에 넣고 고정 URL `/practice/feedback/result`를 반환한다.
- 결과 GET은 세션 값을 읽은 즉시 제거한다.
- 새로고침, 새 탭, 세션 손실, finalize 성공 후 redirect 응답 유실 시 동일 결과를 복원하지 못하고 홈으로 간다.
- DB에는 `FeedbackResult`가 남아 있지만 결과 URL은 어떤 attempt인지 식별하지 않는다.

**근거**

- `src/main/java/com/opicnic/opicnic/controller/PracticeAttemptApiController.java:88-93`
- `src/main/java/com/opicnic/opicnic/controller/PracticeFeedbackController.java:13-22`
- `src/main/resources/templates/practice/feedback.html:34-47`

**최소 재현**

- 연습 완료 후 결과 화면을 새로고침한다.
- 같은 URL을 새 탭에서 연다.
- 둘 다 기존 결과가 아니라 홈으로 이동하는지 확인한다.

**선행 결정과 권장 방향**

- 결과 URL의 수명을 “영구 기록”으로 할지 “최근 attempt 결과”로 할지 정한다.
- 안정적인 방향은 owner-scoped `attemptId` 기반 DB 조회 URL을 제공하는 것이다.
- 이를 완전하게 구현하려면 PC-19의 ordinal/mode/slot 영속화와 기술 감사 `DATA-01`의 멱등 finalize가 선행되거나 함께 설계되어야 한다.

**완료 조건**

- 결과 URL 새로고침과 새 탭 열기가 같은 owner에게 같은 결과를 보여준다.
- 다른 사용자는 접근할 수 없다.
- 존재하지 않거나 권한 없는 result는 404/403 정책대로 처리한다.
- finalize 응답이 유실돼도 사용자가 저장된 결과로 복귀할 방법이 있다.

---

### PC-13 [P2, READY]. “전체 보기”는 실제로 최근 20문항만 보여준다

**현재 동작**

- 분석 화면은 최근 기록 옆에 “전체 보기” 링크를 제공한다.
- `HistoryController.list()`는 `PageRequest.of(0, 20)`만 조회한다.
- 목록에는 pagination이나 더 보기 동작이 없다.
- 21번째 이전 기록은 DB에 남지만 UI에서 접근 경로가 사라진다.

**근거**

- `src/main/resources/templates/analytics/analytics.html:37-45`
- `src/main/java/com/opicnic/opicnic/controller/HistoryController.java:31-38`
- `src/main/resources/templates/analytics/history.html:24-46`

**최소 재현**

- 한 회원에게 21개 이상의 `FeedbackResult` fixture를 저장한다.
- `/analytics/history`에서 20개만 보이고 다음 페이지가 없는지 확인한다.

**수정 경계**

- 실제 전체를 한 번에 로드하지 말고 pagination 또는 cursor 방식으로 노출한다.
- 이전 기록 URL의 owner scope는 현재 유지한다.

**완료 조건**

- 21번째 이후 기록에 UI로 접근할 수 있다.
- 페이지 이동 후 정렬이 최신순으로 안정적이며 누락/중복이 없다.
- “전체 보기” 문구를 유지한다면 전체 이력을 탐색할 수 있다.

## 5. 분석·코칭·시험 계획의 숫자 의미

### 현재 화면별 데이터 범위

| 화면 | 실제 조회 범위 | 현재 표시 단위 |
|---|---|---|
| 학습 분석 | 전체 `FeedbackResult`, 조회 때 재계산 | 총계는 문항, 유형/콤보는 “회” |
| 코칭 | 생성 당시 최근 30문항 snapshot | 문항 |
| 시험 계획 | 전체 `FeedbackResult`, 조회 때 재계산 | 문항 점수를 콤보/유형 추천으로 변환 |
| 오늘 할 일 | 오늘 생성된 distinct `attemptId` | “콤보”라고 표시 |
| 홈 오늘 요약 | 오늘 화면과 같은 계산 | “콤보” |

이 차이는 단순 문구 오류가 아니다. PC-08, PC-14, PC-15, PC-19를 따로 고치기 전에 PD-01의 표준 단위를 결정해야 한다.

### PC-07 [P1, MIXED]. 코칭 등급 사다리의 핵심 수치가 하드코딩이다

**현재 동작**

- 모든 사용자와 모든 과거 리포트에 다음 값이 들어간다.
  - 현재 등급: `IM3`
  - 목표 등급: `IH`
  - 평균: `3.3`
  - 목표선: `3.8`
- 템플릿은 이를 실제 개인의 “현재”, “목표”, “평균”으로 표시한다.
- 코칭 내용 생성 자체는 `SurveyProfile.targetGrade`를 사용하므로 같은 화면 안에서도 목표 source가 둘이다.

**근거**

- `src/main/java/com/opicnic/opicnic/controller/CoachingController.java:37-55,59-70`
- `src/main/resources/templates/analytics/coaching-report-card.html:22-41`
- `src/main/java/com/opicnic/opicnic/service/CoachingService.java:122-151`

**최소 재현**

- 목표가 IH가 아닌 회원 또는 평균이 3.3이 아닌 fixture로 코칭 화면을 연다.
- 현재/목표/평균/목표선이 여전히 고정값인지 확인한다.
- 과거 리포트 상세도 모두 같은 값인지 확인한다.

**수정 경계**

- 단순히 현재 계산값으로 바꾸면 과거 리포트 상세가 생성 당시 snapshot이 아니라 오늘 값으로 바뀐다.
- 최신 화면과 과거 상세의 의미를 분리해야 한다.
- 과거 리포트에 등급·목표·평균·rubric version을 snapshot으로 저장할지는 PD-02, PD-04와 함께 결정한다.

**최소 안전 수정**

- 실제 값을 제공할 수 있을 때까지 하드코딩 사다리를 숨기거나 “예시”로 명확히 표시한다.

**완료 조건**

- 다른 목표·평균의 두 사용자가 서로 다른 올바른 값을 본다.
- 과거 리포트는 정해진 정책에 따라 생성 당시 값 또는 명시적인 현재 값을 표시한다.
- 3문항으로 코칭은 생성 가능하지만 시험 등급은 5문항부터인 현재 최소 표본 차이도 UI에서 모순 없이 설명한다.

---

### PC-08 [P1, DECISION]. “오늘 콤보 완료”는 실제로 모든 종류의 attempt 수다

**현재 동작**

- 오늘 생성된 `FeedbackResult`에서 distinct `attemptId`를 센다.
- 영속 결과에 `PracticeMode`가 없다.
- 유형별 1문항 연습도 `PracticeMode.COMBO`로 attempt를 만든다.
- 모의고사 15문항도 distinct attempt 하나다.
- 결과적으로 유형 1문항, 콤보 2~3문항, 모의고사 15문항이 각각 “콤보 1개 완료”로 표시된다.

**근거**

- `src/main/java/com/opicnic/opicnic/controller/TodayController.java:63-74`
- `src/main/java/com/opicnic/opicnic/controller/HomeController.java:82-90`
- `src/main/java/com/opicnic/opicnic/controller/PracticeTypeController.java:53-55`
- `src/main/java/com/opicnic/opicnic/controller/HomeController.java:140-143`
- `src/main/java/com/opicnic/opicnic/domain/FeedbackResult.java:24-35`

**최소 재현 fixture**

같은 날 한 회원에게 다음을 순서대로 완료시킨다.

1. 유형별 1문항 attempt 1개
2. C1 콤보 attempt 1개
3. 모의고사 attempt 1개

현재는 오늘 완료가 3 “콤보”로 표시된다.

**선행 결정**

- 오늘 목표가 실제 콤보 attempt 수인지,
- 연습 세션 수인지,
- 문항 수인지,
- 난이도를 고려한 학습량인지 정한다.

**완료 조건**

- PD-01에서 정한 단위를 DB에서 복원할 수 있다.
- TYPE/COMBO/MOCK_EXAM fixture가 각각 의도한 만큼만 오늘 목표에 기여한다.
- 홈과 `/today`가 동일한 서비스/쿼리로 같은 숫자를 표시한다.

---

### PC-09 [P1, DECISION]. 시험 계획이 진단 결과와 목표 등급을 사용하지 않는다

**현재 동작**

- `ExamPlanService.buildPlan()`은 `diagnosis`, `target`, 시험일, 하루 시간, 주 공부 일수, 결과 목록을 받는다.
- `diagnosis`는 전혀 읽지 않는다.
- `target`은 `buildMessage()`에 전달되지만 그 메서드도 읽지 않는다.
- 하루 목표는 `max(1, dailyMinutes / 15)`이고 주간 목표는 여기에 공부 일수만 곱한다.
- 메시지는 남은 날짜 구간만 보고 선택한다.

**영향**

- NH→AL과 IM3→IH 사용자의 계획이 같다.
- 목표 등급만 바꿔도 추천량과 메시지가 바뀌지 않는다.
- 실제 진도가 부족해도 날짜만 맞으면 “지금 페이스면 핵심은 커버돼요”라고 표시될 수 있다.

**근거**

- `src/main/java/com/opicnic/opicnic/service/ExamPlanService.java:67-83,186-192`
- `src/main/resources/templates/exam/prep.html:55-75`

**최소 재현**

- examDate/dailyMinutes/studyDays/results를 고정한다.
- diagnosis와 target만 극단적으로 바꾼 두 `StudyPlan`이 완전히 같은지 단위 테스트한다.

**선행 결정**

- “개인화 계획”이 목표별 요구 점수 gap을 반영할지,
- 남은 일수에 따라 총 필요 연습량을 역산할지,
- 사용 가능한 시간 안에서 현실적인 계획만 제시할지 정해야 한다.

**완료 조건**

- 목표·현재 진단·남은 날짜 중 제품이 사용한다고 약속한 값이 실제 계획에 영향을 준다.
- “현재 페이스” 문구는 실제 완료량/필요량을 비교했을 때만 사용한다.
- 입력 변화에 따른 table-driven 계획 테스트가 있다.

---

### PC-14 [P2, DECISION]. 콤보별 `N회`는 문항 행 수다

**현재 동작**

- `buildWeakCombos()`가 category별 `FeedbackResult`를 묶고 `group.size()`를 count로 반환한다.
- 분석 화면은 이를 `N회`라고 표시한다.
- C1/C2/C3 한 번은 보통 3행이므로 3회, C4/C5 한 번은 2행이므로 2회로 표시된다.

**근거**

- `ExamPlanService.buildWeakCombos():85-105`
- `src/main/resources/templates/analytics/analytics.html:175-219`

**최소 재현**

- C1 attempt 하나의 결과 3행을 저장한다.
- 분석 화면의 C1 count가 3회인지 확인한다.

**수정 경계**

- “문항”으로 문구만 고칠 수도 있지만 제품이 콤보별 성적을 보여준다는 의미와 어긋날 수 있다.
- 콤보 횟수라면 distinct attempt 또는 영속 attempt/slot을 세어야 한다.
- 모의고사 콤보를 포함하려면 PC-19가 선행되어야 한다.

**완료 조건**

- 한 번의 C1/C5 연습이 각각 동일하게 콤보 1회로 집계되거나, 문항 수라면 명시적으로 문항이라고 표시된다.
- 평균 점수의 분모도 선택한 단위와 일치한다.

---

### PC-15 [P2, DECISION]. “이번 주 N회 연습”의 N은 과거 표본 수이고 과제에 만료가 없다

**현재 동작**

- 코칭은 가장 약한 유형의 과거 `count()`를 그대로 `"유형 N회 연습"` 문구에 넣는다.
- 이 값은 앞으로 해야 할 권장 횟수가 아니라 분석에 사용한 과거 문항 수다.
- `/today`는 날짜/주차와 무관하게 가장 최신 코칭 리포트의 과제를 계속 “이번 주”로 표시한다.
- `CoachingReport`에는 유효 주차나 만료일이 없고 완료 boolean 하나만 있다.

**근거**

- `CoachingService.fillGapsAndPostProcess():336-340`
- `TodayController.java:77-83`
- `CoachingReport.java:27-33`
- `today.html:59-73`

**예시**

과거에 TYPE_3을 12문항 연습했고 이 유형이 가장 약하면 “TYPE_3 유형 12회 연습”이 이번 주 미래 과제로 표시된다. 몇 달 후에도 새 리포트를 만들지 않으면 같은 과제가 이번 주 과제로 남는다.

**선행 결정**

- 과제 횟수를 고정 정책, 점수 gap, 사용 가능 시간 중 무엇으로 계산할지,
- 과제 주차의 시작/끝과 새 리포트 생성 시 상태 전이를 어떻게 할지 정한다.

**완료 조건**

- 과거 표본 수와 미래 목표 횟수가 서로 다른 필드/변수로 표현된다.
- 오래된 과제가 현재 주 과제로 표시되지 않는다.
- 주차 경계, 새 리포트, 완료 토글에 대한 clock 기반 테스트가 있다.

---

### PC-16 [P2, READY]. 시험 당일도 이미 지난 시험으로 처리한다

**현재 동작**

- `daysLeft <= 0`이면 모두 `daysLeft=0`, 목표량 0, “시험일이 이미 지났습니다”를 반환한다.
- UI는 오늘 날짜 선택을 허용한다.
- 시험 당일, 어제 시험, 오래전 시험이 모두 D-0과 같은 plan으로 접힌다.

**근거**

- `ExamPlanService.buildPlan():70-73`
- `src/main/resources/templates/exam/prep.html:41-57,240-245`
- `src/main/resources/templates/today.html:40-55`

**완료 조건**

- `daysLeft < 0`, `== 0`, `> 0`을 서로 구분한다.
- 시험 당일 문구와 목표가 정해진 정책대로 표시된다.
- `Clock` 또는 명시적 날짜를 주입해 경계 테스트를 작성한다.

---

### PC-17 [P2, READY]. 기록이 없어도 “기록 기반 약한 순 추천”이라고 표시한다

**현재 동작**

- `buildWeakCombos()`와 `buildWeakTypes()`는 기록이 없어도 모든 category/type을 count 0으로 만든다.
- 미연습 항목을 먼저 정렬하므로 동률에서는 사실상 enum 순서가 남는다.
- 시험 화면은 처음 두 개를 “약한 순 추천 · 기록 기반”으로 표시한다.
- 신규 사용자는 C1/C2, TYPE_1/TYPE_2가 개인 약점인 것처럼 보게 된다.

**근거**

- `ExamPlanService.java:85-124`
- `exam/prep.html:74-100,118-136`

**완료 조건**

- 데이터가 없을 때 “추천 시작 순서” 또는 “미연습”으로 명확히 구분한다.
- 기록 기반이라는 문구는 실제 비교 가능한 기록이 있을 때만 나온다.
- 신규 회원/일부 유형만 연습한 회원/충분한 회원의 화면 테스트가 있다.

---

### PC-18 [P2, DECISION]. 개별 피드백과 시험 추정의 등급 공식이 서로 다르다

**현재 동작**

- 개별 답변 등급은 `FeedbackService.computeGrade()`가 5개 점수 평균을 다음 경계로 변환한다.
  - AL 4.5, IH 3.8, IM3 3.2, IM2 2.6, IM1 2.0, 그 외 IL
- 시험 진단은 `ExamPlanService.estimateGrade()`가 항목별 가중 평균의 평균을 다음 경계로 변환한다.
  - AL 4.3, IH 3.8, IM3 3.3, IM2 2.8, IM1 2.3, IL 1.8, 그 외 NH
- 평균 3.2 등 경계 구간에서 동일한 점수가 서로 다른 등급이 된다.
- 무응답 DTO는 IL로 고정하지만 시험 집계에서는 NH가 될 수 있다.

**근거**

- `FeedbackService.java:205-213,251-264`
- `ExamPlanService.java:50-64,176-184`
- 기술 감사 `SCORE-02`의 평가 제외 0점 문제도 함께 고려해야 한다.

**선행 결정**

- 개별 문항에 OPIc 등급을 붙이는 것 자체가 유효한지,
- 시험 추정과 개별 피드백이 같은 rubric을 사용해야 하는지,
- 최소 표본과 유형 coverage를 어떻게 요구할지 정한다.

**완료 조건**

- 동일 rubric을 쓴다고 약속하는 화면끼리는 경계값이 하나의 source of truth에서 나온다.
- 등급 경계 table test가 있다.
- 평가 제외 항목은 분모에서 제외된다.
- 최소 표본 미달은 등급 대신 “추정 불가/연습 지표”로 표시한다.

---

### PC-19 [P2, MIXED]. 모의고사 콤보 provenance가 영구 손실된다

**현재 동작**

- 모의고사는 5개 콤보 슬롯을 조립하지만 반환 타입은 평평한 `List<QuestionDto>`다.
- `QuestionDto`에는 slot index, combo pattern/category, practice mode가 없다.
- `HomeController`는 모의고사 전체를 attempt 하나로 만들며 attempt-level `comboPatternKey/category`를 null로 둔다.
- finalize는 attempt의 null 값을 15개 모든 결과에 복사한다.
- `FeedbackResult`에도 mode나 slot ordinal이 없다.

**영향**

- 모의고사의 C1~C5 성적을 어느 콤보에 귀속할지 복원할 수 없다.
- PC-08의 오늘 진도와 PC-14의 콤보별 통계를 정확히 만들 수 없다.
- 같은 attempt 안의 문항 순서도 DB 고유 필드로 보존되지 않는다.

**근거**

- `MockExamService.createMockExam():32-56`
- `QuestionDto.java:13-18`
- `HomeController.java:140-143`
- `PracticeAttemptApiController.java:203-213`
- `FeedbackResult.java:24-35`

**수정 경계**

- 최소 필요 데이터 후보:
  - `practiceMode`
  - `questionOrdinal`
  - `comboSlotOrdinal`
  - `comboPatternKey`
  - `comboCategory`
- attempt 단위 메타데이터와 문항/slot 단위 메타데이터를 구분한다.
- 기술 감사 `DATA-01`의 unique `(attemptId, questionOrdinal)` 및 멱등 finalize 설계와 함께 처리하는 것이 안전하다.

**완료 조건**

- 모의고사 15문항을 조회해 5개 콤보 슬롯과 각 category를 원래 순서대로 복원할 수 있다.
- `(attemptId, questionOrdinal)`이 중복되지 않는다.
- 콤보/유형/모의고사를 명시적으로 구분할 수 있다.
- 기존 데이터의 null mode/slot 처리 또는 migration 정책이 있다.

---

### PC-24 [P3, READY]. 코칭 진입 조건의 단위와 설정값 문구가 일치하지 않는다

**현재 동작**

- 조건은 `FeedbackResult` 문항 수다.
- teaser는 “N회 더 연습”이라고 한다.
- 코칭 페이지는 “N문항 이상”이라고 한다.
- 분석 화면은 설정값과 무관하게 “3문항 이상”을 하드코딩한다.

**근거**

- `CoachingService.buildTeaser():434-438`
- `analytics/coaching.html:26-30`
- `analytics/analytics.html:82-84`
- `AnalyticsController.java:29-30,72`

**완료 조건**

- 모든 화면이 동일 설정값과 동일 단위 문구를 사용한다.
- 설정을 3이 아닌 값으로 바꾼 view/controller 테스트가 있다.

## 6. 제품으로 보이지만 실제 동작이 없는 기능

### PC-20 [P2, READY]. 알림 설정은 저장만 되고 실제 알림을 만들지 않는다

**제품 계약**

마이페이지는 다음 설정을 실제 기능처럼 제공한다.

- 시험 일정 및 마감일 알림
- 새 스터디 게시글/댓글 알림
- 학습 내용 복습 알림

**현재 동작**

- 세 boolean을 DB에 저장하고 다시 렌더링하는 코드만 있다.
- 알림 생성 entity/service, scheduler, queue, 발송 채널 연동을 찾지 못했다.
- 스터디 게시판은 문서상 비활성화했지만 설정은 계속 노출된다.
- admin header의 알림 dropdown과 `/notifications` 링크는 실제 사용자 알림 시스템의 증거가 아니다.

**근거**

- `src/main/java/com/opicnic/opicnic/domain/NotificationSetting.java:12-19`
- `src/main/java/com/opicnic/opicnic/controller/MyPageController.java:38-43,56-68`
- `src/main/resources/templates/mypage/mypage.html:180-227`
- 저장소 전체에서 `@Scheduled`, 알림 발송 service, notification controller가 없음
- `docs/backlog.md:161`은 스터디 게시판 비활성화를 기록한다.

**수정 선택지**

1. 실제 알림을 구현한다.
2. 아직 제공하지 않는 설정을 UI에서 제거한다.
3. 명시적으로 “준비 중”이며 저장되지 않는 preview로 바꾼다.

기능을 구현하지 않고 boolean 저장만 유지하는 것은 완료 조건이 아니다.

**완료 조건**

- UI에 남긴 각 설정은 켰을 때 관찰 가능한 알림 동작이 있다.
- 지원하지 않는 스터디 게시판 알림은 노출되지 않는다.
- 설정 on/off에 따른 생성·미생성 테스트와 중복 발송 방지 기준이 있다.

## 7. AI 채점 유효성과 신뢰

### PC-22 [P2, DECISION]. 등급·점수의 제품 유효성을 검증하거나 버전별로 추적할 수 없다

**확정된 현재 상태**

- LLM의 구조화 JSON, 일부 realistic sample, 태그/리포트 shape을 확인하는 테스트는 있다.
- 그러나 사람 전문가가 라벨링한 답변과 비교하는 gold dataset, 등급 허용 오차, 유형별 semantic regression threshold는 찾지 못했다.
- `FeedbackResult`에는 사용한 모델, prompt/rubric version이 저장되지 않는다.
- `CoachingReport`에도 분석 대상 result ID/cutoff, 모델/rubric version이 없다.
- 프롬프트와 모델이 바뀌어도 과거·현재 점수가 같은 시계열 평균에 섞인다.

**근거**

- `src/main/java/com/opicnic/opicnic/domain/FeedbackResult.java`
- `src/main/java/com/opicnic/opicnic/domain/CoachingReport.java`
- `src/test/java/com/opicnic/opicnic/service/FullPipelineEndToEndTest.java`
  - 실제 API 결과의 shape/집계 파이프라인을 보지만 전문가 정답과 비교하지 않는다.
- `docs/backlog.md:124-158`
  - temperature/JSON schema/수동 품질 확인은 기록되어 있으나 calibration 기준은 없다.

**이 항목이 의미하지 않는 것**

- 현재 피드백 문장이 무조건 나쁘다는 주장 아니다.
- 공식 OPIc 등급과 법적으로 동일하다고 주장했다는 판정도 아니다.
- 기술적으로 JSON이 안정적이라는 것과 등급이 실제 시험 결과를 예측한다는 것은 다른 검증 문제라는 뜻이다.

**선행 결정**

- 현재 점수를 “OPIc 예상 등급”으로 판매할지 “내부 연습 지표”로 표시할지 정한다.
- 예상 등급을 유지하려면 필요한 검증 수준과 책임자를 정한다.

**권장 작업 단위**

1. TYPE별·실력 구간별 익명 평가 세트를 만든다.
2. 한 명 이상 전문가의 항목 점수/등급과 허용 오차를 정의한다.
3. 모델·prompt·rubric 버전을 저장한다.
4. 모델/프롬프트 변경을 semantic regression gate로 막는다.
5. 버전이 다른 점수를 같은 trend에 섞을지 표시/정규화 정책을 정한다.

**완료 조건 예시**

- 평가 세트와 기대값이 저장소 또는 접근 통제된 별도 자산에 버전 관리된다.
- 유형별 최소 샘플 수와 허용 오차가 문서화된다.
- 새 모델/프롬프트가 기준을 통과하지 못하면 배포되지 않는다.
- 각 결과와 코칭 리포트에서 생성 버전을 추적할 수 있다.

---

### PC-23 [P2, DECISION]. 음성 외부 전송·보관·삭제에 대한 제품 계약이 없다

**확정된 현재 상태**

- 사용자의 녹음은 Groq Whisper로 전송된다.
- STT 텍스트와 상세 피드백은 `FeedbackResult`에 기한 없이 저장된다.
- 로그인 화면은 이용약관과 개인정보 처리방침에 동의한다고 표시하지만 두 문구는 링크가 아니다.
- 계정 탈퇴, 학습 기록 삭제, 데이터 export, 보관기간 설정 경로를 찾지 못했다.

**근거**

- `src/main/java/com/opicnic/opicnic/service/STTService.java`
- `src/main/java/com/opicnic/opicnic/domain/FeedbackResult.java:37-102`
- `src/main/resources/templates/auth/login.html:79-83`
- 저장소 전체 route 검색에서 terms/privacy/delete-account/data-delete 경로 없음

**판정 경계**

- 이 문서는 법률 위반을 판정하지 않는다.
- 다만 음성 학습 제품의 사용자가 외부 전송, 저장 범위, 삭제 가능성을 알 수 없다는 신뢰·제품 계약 공백은 확정적이다.

**선행 결정**

- 원본 audio 보관 여부와 실제 외부 제공자 처리 조건
- STT/피드백/코칭 보관기간
- 개별 기록 삭제, 전체 학습 데이터 삭제, 계정 탈퇴의 범위
- 삭제 후 집계/코칭 snapshot 처리

**완료 조건**

- 로그인 전 접근 가능한 실제 약관/개인정보 문서 링크가 있다.
- 음성 외부 처리와 저장되는 데이터가 평이한 문구로 설명된다.
- 정한 삭제/탈퇴 동작이 owner scope와 cascade 정책을 지키며 테스트된다.
- 보관기간이 있다면 자동 만료가 검증된다.

## 8. 먼저 사람이 결정해야 하는 제품 정책

### PD-01. 진도와 통계의 표준 단위

다음 중 하나 또는 명시적 변환 규칙을 정해야 한다.

- 문항 수
- 콤보 슬롯 수
- practice attempt 수
- 모의고사 수
- 예상 학습 시간/가중 학습량

결정은 최소한 다음 화면에 동시에 적용한다.

- 홈 오늘 요약
- `/today`
- 분석의 유형/콤보 횟수
- 코칭 생성 최소 조건
- 시험 계획의 일/주 목표

PC-08, PC-14, PC-15, PC-19의 선행 결정이다.

### PD-02. 목표 등급의 단일 source of truth

현재는 두 값이 있다.

- `SurveyProfile.targetGrade`: 온보딩과 코칭 생성에서 사용
- `ExamSchedule.targetGrade`: 시험 계획과 오늘 화면에서 사용

선택지 예시는 다음과 같다.

1. 회원의 장기 목표는 profile 하나이고 시험 일정은 이를 참조한다.
2. 시험 일정마다 목표를 snapshot으로 갖고, 활성 일정의 목표가 현재 목표다.
3. 둘을 유지하되 화면에 서로 다른 의미를 명시한다.

PC-06, PC-07, PC-09의 선행 결정이다.

### PD-03. 분석 범위와 freshness

현재 범위가 제각각이다.

- 분석/시험 계획: 전체 이력 실시간 재계산
- 코칭: 생성 시 최근 30문항 snapshot
- 오늘 과제: 최신 코칭 report를 기간 제한 없이 사용

결정할 것:

- 최근 N문항, 최근 N attempt, 기간 기준, 전체 이력 중 어떤 범위를 쓸지
- 과거 리포트가 stale임을 언제 표시할지
- 새 결과가 몇 개 쌓이면 재분석을 권할지
- 다른 rubric/model version을 같은 통계에 포함할지

### PD-04. 등급 표시의 의미와 최소 표본

결정할 것:

- 개별 한 문항에도 OPIc 등급을 붙일지
- 5문항이면 전체 등급 추정에 충분한지
- 최소 attempt 수와 TYPE/category coverage를 요구할지
- 전문가 calibration 전에는 “내부 연습 지표”로 부를지

PC-07, PC-18, PC-22의 선행 결정이다.

### PD-05. 미연습과 약점의 추천 우선순위

현재 미연습 항목을 실제 저점 약점보다 항상 먼저 정렬한다. 다음을 결정한다.

- coverage 확대와 약점 보완 중 무엇을 먼저 추천할지
- 최소 표본 전에는 “약점”이라는 표현을 쓰지 않을지
- 현재 난이도에서 출제 불가능한 category를 추천에서 제외할지

### PD-06. 주 3/5/7일 설정의 실제 의미

현재 `studyDaysPerWeek`은 주간 목표 곱셈에만 쓰이고 오늘 화면은 요일과 무관하게 매일 같은 목표를 보여준다.

결정할 것:

- 사용자가 실제 공부 요일을 선택할지
- 비학습일에는 목표 0/휴식/이월 중 무엇을 표시할지
- 주간 미달분을 남은 공부일에 재분배할지

### PD-07. 결과 URL과 임시 attempt의 복구 수준

결정할 것:

- 녹음 중 브라우저 이탈을 어디까지 복구할지
- STT/LLM 완료 후 finalize 전 상태를 얼마 동안 보존할지
- 제출된 결과 URL을 영구 기록으로 제공할지
- 서버 재시작/다중 인스턴스에서도 진행 중 attempt를 보장할지

기술 감사 `DATA-01`의 상태 머신/멱등성 결정과 연결된다.

### PD-08. 알림 기능을 출시 범위에 둘지

구현할 계획이 없다면 설정 UI를 제거한다. 구현한다면 채널, 시간대, 중복 방지, 동의/해지, 실패 재시도까지 제품 범위로 확정한다.

### PD-09. 데이터 보관과 삭제

PC-23의 신뢰 문구만 추가해서 끝나지 않는다. 실제 retention/delete 동작과 다음 데이터의 cascade 정책을 정한다.

- `SurveyProfile`
- `FeedbackResult`
- `FeedbackTag`
- `CoachingReport`
- `ExamSchedule`
- `NotificationSetting`
- 진행 중 attempt/session 데이터

## 9. 권장 구현 순서

### 1단계 — 데모를 막는 확정 기능 오류

**✅ 8개 항목 모두 완료 (2026-08-13), 상세는 각 항목 섹션과 `docs/CHANGELOG.md` 참고**

1. ✅ PC-01 모의고사 자기소개 실패
2. ✅ PC-02 돌발 풀
3. ✅ PC-03 유형별 내 주제
4. ✅ PC-04 unsupported category의 조용한 fallback 제거
5. ✅ PC-05 거주 주제 보존
6. ✅ PC-10 녹음 이탈 경고
7. ✅ PC-11 최소 선택 서버 검증
8. ✅ PC-21 중복 온보딩 멱등 처리

### 2단계 — 데이터 계약 결정과 영속 모델

1. PD-01 진도 단위
2. PD-02 목표 등급 source
3. PD-04 등급 의미
4. PC-19 mode/ordinal/slot provenance
5. 기술 감사 `DATA-01` 멱등 finalize와 unique constraint

이 단계 없이 PC-08/PC-14를 화면 계산만 바꾸면 다음 모드 추가 때 다시 깨질 가능성이 높다.

### 3단계 — 분석·코칭·계획 정합성

1. PC-07 코칭 하드코딩 제거
2. PC-08 오늘 진행률
3. PC-09 개인화 계획
4. PC-14 콤보 횟수
5. PC-15 주간 과제
6. PC-16 시험 당일
7. PC-17 무기록 추천 문구
8. PC-18 등급 공식
9. PC-24 조건 문구

### 4단계 — 복구·신뢰·출시 표면

1. PC-12 결과 URL 복구
2. PC-13 기록 pagination
3. PC-20 알림 구현 또는 제거
4. PC-22 AI 평가/버전 체계
5. PC-23 개인정보·삭제·보관 계약

## 10. 공통 회귀 fixture

### Fixture A — 단위 정합성

한 회원이 같은 날 다음을 완료한다.

- C1 콤보 1회: 3문항
- 유형별 연습 1회: 1문항
- 모의고사 1회: 15문항, 콤보 슬롯 5개

이 fixture 하나로 다음을 검증한다.

- 오늘/홈 진행률
- 총 문항 수
- 유형별 count
- 콤보별 count
- 모의고사 category 귀속
- 코칭 최소 조건

### Fixture B — 신규/표본 부족/충분 사용자

- 0문항: 기록 기반 약점 문구 금지
- 3문항: 코칭 생성 조건과 단위 문구
- 5문항: 시험 등급 최소 조건
- 여러 유형·여러 attempt: 충분한 coverage 정책

### Fixture C — 설정 보존

- 온보딩 WITH_FAMILY + 12개 주제
- 마이페이지 무변경 저장
- ALONE으로 변경
- 11개로 감소 요청
- 두 탭 중복 온보딩 제출

### Fixture D — 시간 경계

- 시험 전날
- 시험 당일
- 시험 다음 날
- 주 경계 직전/직후
- KST 날짜 경계

테스트에서 시스템 시간을 직접 사용하지 말고 `Clock` 또는 명시적 기준일을 주입하는 것이 좋다.

## 11. 확인 범위와 제한

### 수행한 확인

- 컨트롤러·서비스·엔티티·템플릿 간 계약 정적 추적
- route와 사용자-facing 문구 대조
- 공개 운영 홈/로그인 화면의 비인증 UX 확인
- 기술 감사 결과와 중복 교차 확인

### 의도적으로 수행하지 않은 것

- 운영 사용자 계정으로 OAuth 로그인
- 운영/로컬 DB 데이터 생성·수정
- 실제 Groq 반복 호출 또는 채점 품질 실험
- 실제 알림 발송
- 공식 OPIc 등급과의 전문가 calibration

따라서 이 문서의 `READY`는 코드상 기대 동작과 오동작이 충분히 확정됐다는 뜻이지, 운영 환경에서 이미 악용/실패를 실행했다는 뜻은 아니다.

## 12. 중복 문서와 source of truth

- API·보안·동시성·배포 리스크의 상세 근거는 [`codebase-risk-audit-2026-08-13.md`](codebase-risk-audit-2026-08-13.md)가 source of truth다.
- OPIc 문제 유형, 콤보, 배경설문, 모의고사 규칙은 루트 [`DOMAIN.md`](../DOMAIN.md)가 source of truth다.
- 현재 클래스·라우트 지도는 루트 [`PROJECT.md`](../PROJECT.md)를 본다.
- `docs/local/`은 과거 조사 기록이며 현재 제품 계약이나 구현 지시로 사용하지 않는다.
- 이 문서는 제품 화면과 사용자 여정의 현재 불일치 및 후속 작업 완료 조건의 source of truth다.
