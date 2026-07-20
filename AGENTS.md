
# AGENTS.md

Behavioral guidelines for coding agents working on this repo. Project-specific context lives elsewhere on purpose — see below.

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

## 5. Documentation Checkpoint

**작업을 끝냈다고 선언하기 전에, 아래 중 해당하는 걸 갱신했는지 확인한다.** 문서 계층(`docs/README.md` 참고)이 있어도 갱신을 깜빡하면 금방 다시 썩는다.

- 기능을 완료/삭제/큰 변경했다 → `docs/CHANGELOG.md`에 한 줄 추가
- 진행 중이던 작업의 상태가 바뀌었다 → `docs/backlog.md`의 Done/Next 갱신
- OPIc 도메인 규칙 자체가 바뀌었다 (드묾) → `DOMAIN.md` 갱신
- 컨트롤러/서비스가 추가·삭제·역할 변경됐다 → `PROJECT.md`의 코드베이스 지도 갱신
- 배포/인프라 구조가 바뀌었다 → `docs/deployment.md` 갱신
- 코드에 남긴 "왜 이렇게 했는지" 주석이 지금 변경으로 더 이상 안 맞다 → 주석도 같이 고침 (코드랑 따로 노는 주석이 제일 위험함)

애매하면 사용자에게 "이거 문서화 갱신 대상인가?"라고 물어본다 — 매번 자동으로 판단하지 않는다.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Before touching code

- OPIc 시험 도메인 규칙(문제 유형, 콤보 카테고리, 배경설문 정책, 모의고사 구성) → **`DOMAIN.md`**
- 코드베이스 지도(컨트롤러/서비스/엔티티 역할, 아키텍처 배경, 제약사항, 빌드 상태) → **`PROJECT.md`**

둘 다 이 파일만큼 자주 읽어야 한다 — 여기 안 둔 이유는 프로젝트마다 안 바뀌는 이 파일(1~5번 규칙)과, OPICnic에만 해당하는 내용을 분리해두기 위해서다.
