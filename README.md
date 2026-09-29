<div align="center">

# OPIcnic

**OPIc 전용 AI 피드백 서비스** — OPIc은 ACTFL 채점 기준을 따르는 시험이라 범용 영어 첨삭으로는 안 맞습니다. OPIcnic은 실제 시험 콤보 규칙으로 문제를 내고, OPIc 전용 루브릭으로 채점해 문항별 개별 피드백과, 여러 답변에 걸친 반복 습관을 잡아내는 코칭 리포트를 제공합니다.

[![Java](https://img.shields.io/badge/Java_21-Virtual_Threads-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/loom/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Groq](https://img.shields.io/badge/Groq-Whisper_%7C_LLM-412991?style=flat-square)](https://groq.com)
[![Deploy](https://img.shields.io/badge/opicnic.xyz-live-22c55e?style=flat-square)](https://opicnic.xyz)

[**라이브 데모 →**](https://opicnic.xyz)

<img src="docs/screenshots/feedback.gif" alt="OPIcnic 개별 피드백 데모" width="720">

</div>

---

**목차**

- [프로젝트 개요](#프로젝트-개요)
- [주요 화면](#주요-화면)
- [핵심 지표](#핵심-지표)
- [아키텍처](#아키텍처)
- [주요 문제 해결](#주요-문제-해결)
- [기술 스택](#기술-스택)
- [실행](#실행)
- [배포](#배포)

---

## 프로젝트 개요

> OPIc은 정해진 콤보 규칙대로 문제 세트가 구성되고 ACTFL 루브릭으로 채점되는 시험입니다. 그런데 수험생이 실제로 힘들어하는 지점은 채점 자체보다, **"무엇을, 어떤 순서로 공부해야 하는지"를 매번 스스로 판단하는 과정**입니다.

OPIcnic은 주제 선택부터 문제, 피드백, 코칭, 학습 계획까지 학습 사이클 전체의 판단을 대신 떠맡습니다.

| 수험생이 매번 고민하던 것 | OPIcnic이 대신 해주는 것 |
|---|---|
| 어떤 주제부터 골라야 할지 모르겠음 | 고득점에 불리한 선택지(직업 관련 주제 등)는 온보딩에서 아예 제외 — 남은 것 중에서만 고르면 됨 |
| 문제가 실제 시험이랑 다르게 나오면 연습한 보람이 없음 | 실제 시험 콤보 규칙 그대로 재현해 출제 |
| 오늘 뭘, 얼마나 풀어야 하는지 매번 판단해야 함 | 콤보 진행률·이번 주 과제·오래 방치한 유형까지 오늘 할 일 화면 하나로 안내 |
| 이 답변이 시험 기준으로 괜찮은 건가 | OPIc 전용 루브릭으로 문항별 피드백 |
| 반복되는 약점을 스스로 못 찾겠음 | 누적된 답변에서 패턴을 잡아내는 코칭 리포트 |
| 시험 전까지 뭘 언제 해야 하지 | 시험일까지 남은 기간 기준으로 학습 계획 역산 |

---

## 주요 화면

**1. 온보딩 — 배경설문**
<br>고득점에 불리한 선택지는 처음부터 제외하고, 남은 것 중에서만 고르면 됩니다.
<img src="docs/screenshots/onboarding.gif" alt="온보딩 — 배경설문" width="600">

**2. 연습 — 문제 풀이 · 녹음**
<br>실제 시험 콤보 규칙 그대로 재현된 문제로 연습합니다.
<img src="docs/screenshots/practice.png" alt="연습 — 문제 풀이/녹음" width="600">

**3. 코칭 리포트**
<br>누적된 답변에서 반복되는 패턴을 잡아냅니다.
<img src="docs/screenshots/coaching.gif" alt="코칭 리포트" width="600">

---

## 핵심 지표

<table>
<tr>
<td align="center">
<strong>100%</strong><br>
<sub>이탈·재시작 시 채점 완료율<br>접수 직후, 채점 중 서버 kill</sub>
</td>
<td align="center">
<strong>22.8% → 0%</strong><br>
<sub>외부 API 45% 장애 시 실패 문항<br>재시도 3회 제한 → 시간 기준</sub>
</td>
<td align="center">
<strong>270s → 54s</strong><br>
<sub>장애 복구 후 채점 완료<br>기존 백오프 → full jitter</sub>
</td>
<td align="center">
<strong>2.7×</strong><br>
<sub>문항 병렬 처리 (실음성)<br>12,886ms → 4,719ms</sub>
</td>
</tr>
</table>

---

## 아키텍처

<img src="docs/images/architecture.png" alt="OPIcnic 아키텍처" width="900">

1. 녹음이 끝나면 브라우저가 presigned URL로 녹음 파일을 S3(Cloudflare R2)에 직접 올립니다. 서버는 파일을 받지 않습니다.
2. 제출하면 서버는 채점 작업을 DB에 기록하고 바로 202로 응답합니다.
3. 워커가 DB에서 문항 단위로 작업을 집어 S3의 녹음을 읽고, STT와 채점을 호출해 문항별로 저장합니다. 외부 API가 실패하면 워커가 재시도합니다.
4. 브라우저는 결과를 폴링해 끝난 문항부터 보여줍니다.

녹음 파일(S3)과 채점 작업(DB)이 요청 밖에 있어서, 사용자가 화면을 떠나거나 서버가 재시작돼도 채점이 이어집니다.

**설계 기준**: MAU 24만 가정, 피크 동시 채점 30건 / 접수 응답 p95 0.5s 이내, 이탈 및 재시작 시 채점 완료율 100%, 외부 API 일시 장애 시 실패 문항 0 ([`docs/performance/slo.md`](docs/performance/slo.md))

---

## 주요 문제 해결

### 1. 외부 API 장애에도 채점 결과를 잃지 않도록 S3와 DB 큐 워커로 비동기 전환

<img src="docs/images/async-before-after.png" alt="동기 처리와 비동기 처리 비교" width="820">

**문제**
- 녹음 파일을 서버 메모리에 올려 요청 안에서 외부 API(STT, LLM)로 동기 채점. 재시도와 채점 결과가 요청에 묶여 있어 요청이 끊기면 함께 사라지는 구조
- 서버 재시작이나 사용자 이탈 시 결과가 유실되고, 외부 API가 실패하면 사용자가 1분 넘는 답변을 다시 녹음

**해결**
- 녹음 파일은 서버 디스크나 메모리 대신 S3에 두고 presigned URL로 직접 업로드. 서버 재시작에도 남고 서버 메모리를 거치지 않음
- 채점 작업은 Kafka 같은 별도 큐 대신 DB 큐에 기록해 결과 저장과 상태 변경을 한 트랜잭션으로 처리, 워커가 문항 단위로 채점
- 사용자가 응답을 기다리지 않게 되어 재시도 한도를 횟수(3회) 대신 접수 후 30분이라는 시간 기준으로 늘리고 지수 백오프
- 실패를 기다리면 풀리는지로 나눠 녹음 파일 문제는 즉시 실패, 429와 5xx, 타임아웃만 재시도
- 몇 초 늦게 보여도 되는 결과라, 서버가 연결을 유지해야 하는 SSE나 롱폴링 대신 요청마다 DB를 읽는 숏폴링으로 전달
- 검증: 접수 직후와 채점 중 각각 서버 프로세스를 kill 후 재기동, mock 외부 API로 호출의 45%를 실패시키는 장애 주입

**결과**
- 서버 재시작이나 사용자 이탈에도 채점 완료율 100%
- 외부 API 일시 장애를 서버 재시도로 흡수 (45% 장애 주입 시 실패 문항: 재시도 3회 제한 22.8% → 시간 기준 재시도 0%)
- 녹음 파일이 서버 메모리를 거치지 않아 힙 부담 감소 (Humongous GC 5분당 25회 → 3회)

설계 결정과 대안 비교: [`docs/adr/0001-async-r2.md`](docs/adr/0001-async-r2.md)

### 2. 외부 API 복구 시 재시도가 한꺼번에 몰리지 않도록 full jitter로 분산

<img src="docs/performance/2026-09-26/retry-storm.png" alt="재시도 방식별 초당 외부 호출" width="820">

**문제**
- 외부 API가 복구되는 순간 밀려 있던 재시도가 한꺼번에 몰려, 막 복구된 제공자에 다시 과부하를 줄 수 있는 구조
- 과부하로 거절된 호출(429)이 다시 재시도를 부르며 복구 뒤에도 채점 완료가 늦어질 수 있음

**해결**
- mock 외부 API로 60초 장애 후 복구(복구 직후 초당 10건 처리)를 재현해 재시도 방식 세 가지를 비교
  - 즉시 재시도: 복구 순간 처리량의 9배가 몰려 429 78%
  - 2배씩 늘린 대기 + 1초 jitter: 호출은 줄었지만 대기가 60초 상한에 붙어 모든 문항이 60초마다 같은 박자로 몰림
- 대기 시간이 아니라 대기 범위를 2배씩 늘리고, 실제 대기는 그 범위 안에서 무작위로 고르는 full jitter(AWS SDK 기본 재시도 방식)로 변경
- 워커 동시 처리 수를 제한해 한 번에 나가는 외부 호출 상한 고정

**결과**
- 기존 백오프 대비 복구 후 채점 완료 270초 → 54초, 거절된 호출(429) 33% → 0.4%
- 복구 직후 초당 최대 호출 39 → 12로 제공자가 처리할 수 있는 양 안에서 고르게 분산
- 즉시 재시도 대비 외부 호출 87% 감소

실험 기록: [`docs/performance/2026-09-26/retry-storm.md`](docs/performance/2026-09-26/retry-storm.md)

### 3. 서버 지표로는 안 보이던 장애를 증상 기반 알림으로

- 외부 모델이 사라져 채점이 전부 실패한 장애가 두 번 있었는데, CPU, 힙, HTTP 에러율은 모두 정상이라 우연히 발견
- 외부 호출의 호출 수, 에러, 지연(RED)과 워커 큐 깊이, 처리 중 수, 최종 실패를 지표로 추가
- 사용자에게 한 약속이 깨지는 증상에만 알림 5개: 채점 실패율, 최종 실패 비율, 워커 멈춤, 접수 지연, 완료 시간 SLO. CPU, 힙 같은 원인 지표에는 걸지 않음
- 알림 규칙은 promtool 단위 테스트로 검증. 이 과정에서 라벨 불일치로 운영에서는 절대 울리지 않던 규칙을 찾아 수정

### 4. 코칭 리포트: 판단은 LLM, 집계는 코드

- 코칭 리포트가 "오류가 거의 없는데 시제, 어휘가 적절하지 않다"처럼 앞뒤 안 맞는 진단을 내는 문제
- 여러 답변에 걸친 패턴 집계를 LLM에 통째로 맡긴 게 원인. 실행마다 집계 결과가 달라짐
- 답변 단위 판단(태그 분류)만 LLM이 하고, 집계, 문턱값, 그룹핑은 코드가 처리하도록 분리해 출력 일관성 확보

<details>
<summary><strong>비동기 전환 이전(동기 구조)에서 한 개선</strong></summary>

- **디스크 I/O 병목 제거** (2026-04): DB pool 확장, VT pinning 가설을 1KB 격리 실험과 JFR로 기각한 뒤 톰캣 멀티파트 임시파일 쓰기가 원인임을 확인, 멀티파트 임계치를 올려 디스크 쓰기를 제거. RPS 96 → 652, 평균 지연 1,100ms → 249ms (Mock STT/LLM, 500VU, 단일 실행)

  <details>
  <summary>2026-04 당시 전후 구조 (릴레이는 이후 되돌림)</summary>

  ```mermaid
  flowchart LR
      subgraph BEFORE["BEFORE — Heap Copy"]
          direction LR
          b1(["Request"]) --> b2["Tomcat"] --> b3["getBytes()\nbyte[] 1MB 힙 적재"] --> b4["ByteArrayResource"] --> b5["STT API"]
      end
      subgraph AFTER["AFTER — InputStream Relay"]
          direction LR
          a1(["Request"]) --> a2["Tomcat"] -->|"InputStream relay"| a3["STT API"]
      end
      BEFORE -.->|"Avg 1,100ms → 249ms · RPS 96 → 652"| AFTER
  ```

  4월엔 (1) `file-size-threshold: 2MB`로 톰캣 임시파일 쓰기 제거, (2) `InputStream` 릴레이로 힙 복사 제거 두 개를 같이 넣었다. 6월에 STT/LLM 자동 재시도가 필요해지면서 스트림을 다시 읽을 수 없어 `byte[]` 버퍼링으로 되돌렸고(`docs/local/2026-06-06`), 8월에 타입까지 정리했다. **지금 코드에 남은 건 (1)뿐이다.** 그 힙 적재의 비용은 2026-09 부하테스트에서 GC 압박으로 측정됐고, 오브젝트 스토리지 직접 업로드로 전환하는 근거가 됐다(`docs/adr/0001-async-r2.md`).

  </details>

- **커넥션 경합 해소**: 채점마다 대상 문항을 DB에서 다시 조회하는 경로가 부하 상황에서 커넥션 경합을 일으킴. 인메모리 캐시를 넣고, 캐시 히트에도 커넥션을 선점하던 `@Transactional` 경계를 함께 제거해 제출 p95 20.5s → 3.73s
- **문항 병렬 처리**: 콤보 2~3문항을 순차 채점하면 외부 대기가 문항 수만큼 누적되어, `StructuredTaskScope`로 문항을 병렬화해 12,886ms → 4,719ms (2.7배, 실음성). 지금은 워커가 문항을 각각 독립된 작업으로 가상 스레드에서 처리

</details>

---

## 기술 스택

| | |
|---|---|
| **Language / Runtime** | Java 21, Virtual Threads |
| **Framework** | Spring Boot 3.4, Spring AI, Spring Security OAuth2 |
| **AI / STT** | Groq Whisper (STT), gpt-oss-120b (채점, 코칭 작성), gpt-oss-20b (태깅). 모델 ID는 설정값으로 분리 |
| **Database** | MySQL 8.0, Spring Data JPA (채점 작업 큐 겸용) |
| **Storage** | Cloudflare R2 (S3 API, presigned URL 직접 업로드) |
| **Cache** | Caffeine (제출 전 연습 상태), ConcurrentHashMap (문제 세트) |
| **Rate Limiting** | Bucket4j (사용자별 시간당 15문항) |
| **Infra** | Oracle Cloud ARM A1 (4 OCPU, 24GB), Docker Compose, Cloudflare |
| **Monitoring** | Prometheus, Grafana, Alertmanager (Discord), Micrometer |

---

## 실행

```bash
cp .env.example .env          # DB_USERNAME/DB_PASSWORD/GROQ_API_KEY 채우기
docker-compose up -d mysql    # MySQL만 (전체를 올리면 앱 컨테이너가 8080을 먼저 잡음)
set -a && source .env && set +a
./gradlew bootRun
```

- `STT_ENABLED=false LLM_ENABLED=false`로 실행하면 외부 API 없이 mock 응답으로 동작합니다.
- R2 키(`R2_*`)가 비어 있으면 인메모리 저장소로 기동합니다(로컬 확인용, 재시작 시 녹음 유실).

## 배포

```bash
cp .env.example .env          # 운영 값 + DISCORD_WEBHOOK_URL(없으면 중단) + R2 키
./deploy.sh
```

`Cloudflare → host Nginx (SSL 종료) → App Nginx → Spring Boot` 구조로 동일 VM에 여러 서비스를 운영합니다. Grafana는 외부에 열지 않고 SSH 터널로 접근합니다.
