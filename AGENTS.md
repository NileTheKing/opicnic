
# CLAUDE.md

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

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
- There is no dedicated surprise-topic question bank yet. Current implementation uses unselected supported topics as a temporary substitute.

### Important Current Classes

- `ComboPracticeService`: creates one short practice combo from `topic + difficulty`.
- `OpicComboPatternProvider`: provides difficulty-specific combo patterns.
- `QuestionAssemblyService`: converts `QuestionSet + ComboPattern` into `QuestionDto` list.
- `MockExamService`: creates full mock exam questions.
- `HomeController`: routes `/practice/random`, `/practice/surprise`, `/practice/mock`.
- `TopicsController`: renders `/practice/topics`.

### Important Constraints

- Do not treat persisted `Combo` as the source of truth for OPIc exam generation.
- Do not claim true OPIc surprise topics are fully implemented.
- Do not hardcode topic counts such as `22`.
- Do not reintroduce difficulty selection into the topic exploration page. Difficulty comes from onboarding/profile.
- Keep `docs/local/` ignored. It is for local development notes.

### Verification Notes

- `./gradlew compileJava` currently passes.
- `./gradlew test` currently fails because `QuestionSetAdminIntegrationTest` still references the old `QuestionSet.difficulty` model.
