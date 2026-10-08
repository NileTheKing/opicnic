# ADR-0002: 외부 호출 예외는 경계에서 우리 예외로 옮긴다

> 상태: **Accepted** (2026-10-08, 구현 완료 `feat/acl`)
> 관련: ADR-0001 4절(워커 실패 분류), `docs/CHANGELOG.md` 2026-09-23 LLM 429 오분류 항목

## 맥락

워커(`ScoringWorker.classify`)는 실패를 "기다리면 풀리는가"로 나눠 재시도를 정한다. 그런데 이 판단이 외부 클라이언트 라이브러리의 예외 모양을 직접 해석하고 있었다.

| 호출 | 클라이언트 | 실패 모양 |
|---|---|---|
| 녹음 파일 읽기 | AWS S3 SDK (R2) | `NoSuchKeyException` |
| STT | RestClient | `HttpClientErrorException` — 상태 코드가 필드 |
| 채점·태깅 | Spring AI | `NonTransientAiException("HTTP 400 - …")` — 상태 코드가 **메시지 문자열에만** |

워커는 앞의 두 모양만 알았다. 그래서 같은 구멍이 두 번 났다.

- 2026-09-18: LLM 429가 rate-limit으로 안 잡혀 짧은 백오프를 탔다. `outcomeOf()` 재사용으로 메움
- 2026-10-08: LLM 400·422가 일시적 실패로 분류돼 **절대 성공하지 않을 요청을 30분간 재시도**했다. 테스트로 재현(`expected: PERMANENT but was: TRANSIENT`)

같은 날 LLM 공급자를 환경변수로 바꿀 수 있게 됐다(Groq ↔ Gemini). 공급자·클라이언트가 늘수록 워커가 알아야 할 모양도 는다.

덤으로, 모델 응답 형식 오류를 `IllegalStateException`으로 표시하고 있어서 우리 코드의 일반 버그도 "LLM 형식 오류"로 분류됐다.

## 결정

**외부 예외는 그 호출을 감싸는 경계에서 우리 예외로 옮겨 던지고, 워커는 우리 예외만 본다.** (DDD의 Anti-Corruption Layer)

| 경계 | 옮기는 곳 | 우리 예외 |
|---|---|---|
| 외부 AI 호출 (STT·채점·태깅) | `ExternalCallMetrics.record()` — 모든 호출이 이미 지나가는 곳 | `ExternalCallException(reason)` — RATE_LIMITED / UNAVAILABLE / BAD_REQUEST / PROVIDER_ERROR |
| 녹음 파일 읽기 | `AudioStorage` 구현(`R2AudioStorage`, `InMemoryAudioStorage`) | `AudioNotFoundException` |
| 모델 응답 파싱·검증 | `LlmService`, `STTService`, `FeedbackService.score` | `InvalidModelOutputException` |

우리 예외에는 **사실**(무슨 일이 있었나)만 담고, **판단**(재시도하나)은 워커가 한다. 400을 영구 실패로 볼지는 채점 정책이라 바뀔 수 있고, 그때 어댑터를 뒤지지 않게.

## 근거

- **`classify()`에 Spring AI 모양을 하나 더 추가** — 몇 줄이면 끝나지만 같은 땜질의 세 번째다. 다음 클라이언트가 오면 또 난다.
- **Spring AI를 걷어내고 다른 클라이언트로** — 어떤 라이브러리든 자기 예외 모양이 있다. 바꿔도 워커가 그걸 해석하는 구조는 그대로다. 오히려 경계에서 옮겨두면 나중에 라이브러리를 바꿀 때 경계만 고치면 된다.
- **모든 의존성에 port/adapter 인터페이스 도입(헥사고날 전면 적용)** — 구현체가 하나뿐인 곳까지 인터페이스가 늘어난다. 문제가 난 경계(외부 호출 실패)에만 적용한다.

## 결과

- `ScoringWorker`에서 AWS·Spring web·Spring AI·Jackson import가 사라졌다. 워커 테스트는 우리 예외만으로 쓴다.
- 번역 테스트는 경계 쪽(`ExternalCallMetricsTest`, `SpringAiErrorFormatTest`)에 있다. 후자는 운영과 같은 Spring AI 오류 처리기를 돌려 실제 메시지 형식으로 고정한다.
- 동작 변화: LLM 400·422 → 즉시 FAILED. 우리 코드의 `IllegalStateException` → 일시적(형식 오류 아님). STT 응답에 `text` 없음 → 30분 재시도에서 형식 오류 3회로.

남는 한계:
- `ExternalCallMetrics`가 계측과 번역 두 일을 한다. 모든 호출이 지나가는 유일한 자리라 같이 뒀다. 이름이 역할을 다 말하지 못한다.
- Spring AI 예외에서 상태 코드를 꺼내는 건 여전히 메시지 정규식이다. 다만 이제 한 곳에만 있고, Spring AI가 형식을 바꾸면 `SpringAiErrorFormatTest`가 깨진다.
- 경계를 거치지 않는 새 외부 호출을 추가하면 다시 날것의 예외가 워커까지 온다. 그 경우 `classify()`는 TRANSIENT로 떨어진다(재시도 쪽이 안전한 기본값).
