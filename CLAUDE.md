
# AGENTS.md

Behavioral guidelines and project context for coding agents working on OPicnic.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## OPicnic Current Project Context

### Current OPIc Domain Model

- `QuestionSet` is the topic-specific question bank.
- Each `QuestionSet` contains `Question` rows for `TYPE_1` through `TYPE_10`.
- `ComboPattern` is a runtime exam pattern, not a persistent DB concept.
- `MockExamService` creates a 15-question mock exam: 1 self-introduction + 5 combo slots.
- The mock exam uses 3 selected-topic combos and 2 surprise-topic combos.
- Surprise combo slots are randomized among the 5 combo slots.
- Surprise topics use a dedicated pool of 23 topics (5 groups) defined in `TopicCatalog.surpriseTopics()`. These are completely separate from the 22 background-survey topics and have their own QuestionSets in DB (DataInitializer V1/V2/V3).

### Important Current Classes

- `ComboPracticeService`: creates one short practice combo from `topic + difficulty`.
- `OpicComboPatternProvider`: provides difficulty-specific combo patterns.
- `QuestionAssemblyService`: converts `QuestionSet + ComboPattern` into `QuestionDto` list.
- `MockExamService`: creates full mock exam questions.
- `HomeController`: routes `/practice/random`, `/practice/surprise`, `/practice/mock`.
- `TopicsController`: renders `/practice/topics`.

### 배경설문 제한 정책

OPicnic은 고득점 전략 기준으로 배경설문 선택지를 의도적으로 제한한다. 빠진 항목은 미구현이 아니라 제외 결정된 것.
- 거주형태: `WITH_FAMILY` / `ALONE` 2개만. 세트 수에 영향 있어서 노출.
- 직업: UI 없음. 직업 관련 주제(직장·출장 등)는 고득점 불리 → 의도적 제외.
- 주제: 22개 중 고득점 추천 주제만 노출.

### Important Constraints

- Do not treat persisted `Combo` as the source of truth for OPIc exam generation.
- Do not hardcode topic counts such as `22` or `23`.
- Do not reintroduce difficulty selection into the topic exploration page. Difficulty comes from onboarding/profile.
- Keep `docs/local/` ignored. It is for local development notes.

### Architecture Notes

**PracticeAttempt / attemptId 설계 배경**

`PracticeAttemptService.createAttempt()`는 서버가 문제를 조립한 뒤 `attemptId → questionIds[]`를 Caffeine 캐시(2시간 TTL)에 저장한다. 클라이언트는 `attemptId`만 받고, 제출 시 오디오 blob + attemptId만 전송한다.

**주목적: 제출-재시도-finalize 3단계 멀티스텝 플로우 지원.**
실패 문항만 재제출할 때 서버가 원본 questionIds를 복원해야 retry 매핑이 가능하기 때문이다.
부수효과로 클라이언트가 question content나 ID를 조작할 수 없게 된다.

캐시(인메모리)를 쓰는 이유: 연습 완료 전의 임시 상태라 DB 영구 저장이 불필요하고, 서버 재시작 시 만료돼도 무해하다.

### Verification Notes

- `./gradlew compileJava` currently passes.
- `./gradlew test` currently fails because `QuestionSetAdminIntegrationTest` requires Docker (Testcontainers). Docker not running = test fails. Code itself is correct.
