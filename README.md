<div align="center">

# Opicnic

**OPIc AI 피드백 서비스** — 음성 답변을 제출하면 LLM이 항목별 피드백 리포트를 생성합니다

[![Java](https://img.shields.io/badge/Java_21-Virtual_Threads-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/loom/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Groq](https://img.shields.io/badge/Groq-Whisper_%7C_Llama--3.3--70b-412991?style=flat-square)](https://groq.com)
[![Deploy](https://img.shields.io/badge/opicnic.xyz-live-22c55e?style=flat-square)](https://opicnic.xyz)

[**라이브 데모 →**](https://opicnic.xyz)

</div>

---

## 핵심 성능 지표

<table>
<tr>
<td align="center">
<strong>79%</strong><br>
<sub>p95 지연 단축<br>1,130ms → 238ms</sub>
</td>
<td align="center">
<strong>3.3×</strong><br>
<sub>병렬 처리 속도<br>4,877ms → 1,474ms</sub>
</td>
<td align="center">
<strong>2.5×</strong><br>
<sub>처리량 향상<br>96 RPS → 242 RPS</sub>
</td>
<td align="center">
<strong>22개</strong><br>
<sub>지원 OPIc 주제<br>C1~C5 콤보 패턴</sub>
</td>
</tr>
</table>

---

## 어떻게 동작하나

```
사용자 음성 녹음
      ↓
  attemptId 검증 (서버가 문제 원본 보관, 클라이언트 조작 차단)
      ↓
  N개 질문 ── VirtualThread #1 ──→ Groq STT ──→ Groq LLM ──→ 피드백
             ── VirtualThread #2 ──→ Groq STT ──→ Groq LLM ──→ 피드백  (동시)
             ── VirtualThread #3 ──→ Groq STT ──→ Groq LLM ──→ 피드백
      ↓
  항목별 피드백 리포트 (어휘·문법·유창성·내용·메인포인트·종합)
      ↓
  FeedbackResult DB 저장 (questionType, comboCategory, surveyTopicName 포함)
```

---

## 엔지니어링 하이라이트

### 1. 디스크 I/O 병목 제거 — p95 1,130ms → 238ms

톰캣은 멀티파트 파일이 기본 임계치(10KB)를 초과하면 디스크에 임시 파일을 씁니다. 음성 파일은 항상 이 임계치를 초과하므로 모든 요청에서 **디스크 I/O Wait**가 발생했습니다.

JFR 프로파일링으로 `jdk.ObjectAllocationSample`에서 대규모 byte[] 복사와 톰캣 디스크 쓰기 이벤트를 확인. `file-size-threshold: 2MB` 설정 하나로 InputStream을 메모리에서 STT API로 직접 릴레이하는 구조로 전환했습니다.

<details>
<summary>병목 분석 과정</summary>

| 단계 | 가설 | 실험 | 결과 |
|------|------|------|------|
| 1 | DB 커넥션 부족 | 커넥션 풀 10 → 50 | 지표 변화 없음. 기각 |
| 2 | 파일 I/O | 1MB → 1KB 음성으로 교체 | p95 132ms로 급감. 확정 |
| 3 | 물리적 증거 | JFR 프로파일링 | 디스크 쓰기 이벤트 포착 |

</details>

---

### 2. Java 21 Structured Concurrency — 실측 3.3배

OPIc 콤보는 2~3개 질문으로 구성되며, 각 질문마다 STT → LLM을 직렬 처리하면 응답 시간이 문항 수에 비례해 증가합니다.

`StructuredTaskScope.ShutdownOnFailure`로 N개 Virtual Thread를 동시에 시작. STT + LLM이 모두 외부 API 대기(I/O bound)이므로 가상 스레드가 대기 시간을 겹쳐서 처리합니다.

```
[Subtask-0] VirtualThread#85 ─────────────────── 2,111ms (순차)
[Subtask-1] VirtualThread#86 ──────── 1,282ms
[Subtask-2] VirtualThread#87 ───────────── 1,484ms
                                         ↑
                               병렬: 1,474ms (가장 느린 subtask에 수렴)
```

| | 순차 | 병렬 |
|---|---|---|
| 문제 0 | 2,111ms | ↘ |
| 문제 1 | 1,282ms | 1,474ms |
| 문제 2 | 1,484ms | ↗ |
| **합계** | **4,877ms** | **1,474ms** |

`CompletableFuture` 대신 Structured Concurrency를 선택한 이유: 실패 시 나머지 작업 취소와 예외 집계를 언어 수준에서 보장받기 위해서입니다. 하나의 subtask가 실패해도 나머지 결과는 보존하고 실패 문항만 `failedIndexes`로 반환합니다.

---

### 3. PracticeAttempt — 클라이언트 신뢰 없는 세션 설계

클라이언트가 제출 시 문제 내용을 함께 보내면 조작이 가능합니다.

문제풀이 시작 시 `attemptId`를 생성하고 `questionIds`를 서버(Caffeine)에 저장합니다. 제출 시 서버가 `attemptId`로 직접 DB에서 문제를 복원해 클라이언트 조작을 원천 차단합니다.

음성 재시도 시에도 서버는 음성 원본을 보관하지 않습니다. 클라이언트가 보관 중인 녹음 Blob으로 실패 문항만 재전송합니다. (모의고사 15문항 기준 서버 힙 보관 vs 클라이언트 재전송 트레이드오프에서 후자 선택)

```
PracticeAttemptStore (interface)
└── CaffeinePracticeAttemptStore  ← 현재
    (future: RedisPracticeAttemptStore)
```

---

### 4. OPIc 콤보 패턴 C1~C5 도메인 모델링

OPIc 공식 출제 구조(콤보 I~V)를 `ComboPattern` record로 모델링. C3 판별이 최우선인 이유: 콤보 III는 `TYPE_6,7,4`와 `TYPE_6,7,8` 두 종류가 존재해 TYPE_4 포함 여부만으로는 C2와 구분 불가능합니다.

```java
public String category() {
    if (questionTypes.contains(TYPE_6) || questionTypes.contains(TYPE_7)) return "C3"; // 우선
    if (questionTypes.contains(TYPE_9) || questionTypes.contains(TYPE_10)) return "C5";
    if (questionTypes.contains(TYPE_5)) return "C4";
    if (questionTypes.contains(TYPE_4)) return "C2";
    return "C1";
}
```

피드백 저장 시 `comboPatternKey`, `comboCategory`, `questionType`, `surveyTopicName`을 함께 저장해 학습 이력 분석 기반을 마련했습니다.

---

## 기술 스택

| | |
|---|---|
| **Language / Runtime** | Java 21, Virtual Threads |
| **Framework** | Spring Boot 3.4, Spring AI, Spring Security OAuth2 |
| **AI / STT** | Groq Whisper (STT), Groq Llama-3.3-70b (LLM) |
| **Database** | MySQL 8.0, Spring Data JPA |
| **Cache** | Caffeine (세션), ConcurrentHashMap (QuestionSet) |
| **Rate Limiting** | Bucket4j (사용자별 10회/시간) |
| **Infra** | Oracle Cloud ARM A1, Docker Compose, Cloudflare SSL |
| **Monitoring** | Prometheus, Grafana, Spring Actuator |

---

## 실행

```bash
docker-compose up -d          # MySQL
export GROQ_API_KEY=...
./gradlew bootRun
```

`spring.ai.openai.enabled=false` 설정 시 외부 API 없이 Mock 응답으로 동작합니다.

## 배포

```bash
cp .env.example .env
./deploy.sh
```

`Cloudflare → host Nginx (SSL 종료) → App Nginx → Spring Boot` 구조로 동일 VM에 여러 서비스를 운영합니다.
