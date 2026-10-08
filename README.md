<div align="center">

# OPIcnic

**OPIc 준비에서 "뭘, 어떻게, 언제" 할지 고민하는 걸 대신 해 주는 서비스**<br>연습할 때마다 문항 피드백이 쌓이고, 쌓인 기록에서 반복되는 버릇을 찾아, 시험일까지 다음에 할 일을 정해 줍니다.

[![Java](https://img.shields.io/badge/Java_21-Virtual_Threads-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org/projects/loom/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_3.4-6DB33F?style=flat-square&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Gemini](https://img.shields.io/badge/Gemini-채점_LLM-4285F4?style=flat-square&logo=googlegemini&logoColor=white)](https://ai.google.dev)
[![Groq](https://img.shields.io/badge/Groq-Whisper_STT-412991?style=flat-square)](https://groq.com)
[![Deploy](https://img.shields.io/badge/opicnic.xyz-live-22c55e?style=flat-square)](https://opicnic.xyz)

[**라이브 데모 →**](https://opicnic.xyz)

<img src="docs/screenshots/loop.png" alt="연습 → 문항 피드백 → 학습 패턴 분석 → 시험 준비 계획" width="820">

<sub>로그인 없이 둘러보기로 들어가면 바로 보이는 예시 기록입니다(실제 채점 결과).</sub>

</div>

---

**목차**

- [시험 준비, 이걸 대신 해 줘요](#시험-준비-이걸-대신-해-줘요)
- [주요 화면](#주요-화면)
- [아키텍처](#아키텍처)
- [핵심 지표](#핵심-지표)
- [주요 문제 해결](#주요-문제-해결)
- [기술 스택](#기술-스택)
- [실행](#실행)
- [배포](#배포)

---

## 시험 준비, 이걸 대신 해 줘요

> OPIc은 정해진 출제 규칙으로 문제가 나오고, 채점자가 15문항 전체를 듣고 등급을 매기는 시험입니다. 수험생이 실제로 힘들어하는 건 영어 자체보다 **"무엇을, 어떤 순서로 공부해야 하는지"를 매번 스스로 판단하는 일**입니다.

- **주제는 뭘 고르지?**<br>
  **온보딩**에서 고득점에 불리한 선택지(직업 관련 등)는 아예 빼고, 남은 것 중에서만 고르게 합니다
- **뭘 연습하지?**<br>
  콤보·문제 유형·주제·돌발·모의고사 중 원하는 **단위**로 — 어떤 단위든 실제 시험 출제 규칙대로 나옵니다
- **내 답변, 뭐가 문제지?**<br>
  **문항 피드백**: 실제 등급 이름으로 답변 수준(IM2, IH…)을 알려주고, 약한 문장을 그대로 인용해 고친 문장을 붙입니다<br>
  **학습 패턴 분석**: 답변이 쌓이면 여러 답변에 반복되는 버릇("감정 없이 사실만 나열")과 유형별 약점을 찾습니다
- **그럼 어떻게 고치지?**<br>
  고친 문장, 내 답을 한 단계 올려 다시 쓴 모범 답안(듣고 따라 말하기), 버릇마다 연습 전략과 이번 주 과제
- **시험까지 언제 뭘 하지?**<br>
  **시험 준비**: 시험일만 정하면 쌓인 기록으로 하루 목표, 약한 콤보·유형, 오래 안 한 유형을 정합니다. 예상 등급도 함께 — IH 이상은 롤플레이 문제 해결(12번), AL은 비교·이슈(14·15번)에서도 그 수준이 나와야 인정합니다

연습 → 피드백 → 패턴 분석 → 그 기록으로 다음 계획 → 다시 연습. 이 순환을 사용자가 아니라 서비스가 돌립니다.

**연습 단위** — 모두 실제 시험의 출제 규칙(콤보 구성, 난이도별 유형)을 따릅니다.

| 단위 | 내용 |
|---|---|
| 콤보 | 한 주제로 2~3문항(묘사 → 루틴 → 경험 등). 실제 시험의 기본 단위 |
| 문제 유형 | 묘사·경험·롤플레이·비교·이슈 중 하나만 집중 |
| 주제 | 고른 주제 하나를 골라 연습 |
| 돌발 | 배경설문에서 고르지 않은 주제(23개 전용 풀) |
| 모의고사 | 자기소개 + 콤보 5개(선택 주제 3, 돌발 2) = 실제 시험과 같은 15문항 |

**로그인 없이 둘러보기**로 예시 기록·학습 패턴 분석·3주 뒤 시험 일정이 채워진 계정으로 바로 체험할 수 있습니다(방문자마다 별도 계정, 7일 뒤 삭제, 무료 LLM 한도를 지키려고 채점은 하루 6문항).

---

## 주요 화면

**1. 문항 피드백** — 답변마다 수준과 고친 문장, 그리고 내 답을 한 단계 올린 모범 답안

<p align="center">
<img src="docs/screenshots/hero.png" alt="내 답변(IM2)과, 내 답을 한 단계 올려 다시 쓴 모범 답안" width="760">
</p>

**2. 학습 패턴 분석** — 쌓인 답변에서 반복되는 버릇을 찾아 근거 문장과 함께 보여줍니다

<p align="center">
<img src="docs/screenshots/coach-summary.png" alt="요약과 예상 등급" width="260"> <img src="docs/screenshots/coach-weak.png" alt="반복되는 버릇과 실제 문장 예시" width="260"> <img src="docs/screenshots/coach-types.png" alt="유형별 약점과 이번 주 과제" width="260">
</p>

- 버릇은 LLM이 짐작하지 않습니다. 문항 채점 때 붙인 태그를 **코드가 답변 단위로 세고**("12개 중 8개 답변"), 3개 이상 답변에서 반복된 것만 약점으로 올립니다. LLM은 그 집계를 문장으로 옮기는 일만 합니다. 예시 문장도 실제 답변에서 코드가 골라 붙입니다.
- 예상 등급은 점수 평균이 아니라 최근 답변 수준의 대표값입니다(채점 기준과 보정 과정: [`docs/performance/2026-10-07-grading-calibration`](docs/performance/2026-10-07-grading-calibration/README.md)).

**3. 시험 준비** — 시험일까지 남은 기간과 쌓인 기록으로 하루 목표와 약한 곳을 역산

<p align="center">
<img src="docs/screenshots/plan.png" alt="시험 준비 — D-day와 학습 계획" width="260"> <img src="docs/screenshots/history.png" alt="기록 — 항목별 평균과 유형별 점수" width="260">
</p>

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

## 핵심 지표

<table>
<tr>
<td align="center">
<strong>7.6s → 0.02s</strong><br>
<sub>채점 접수 응답<br>비동기 전환 (S3 + DB 큐 워커)</sub>
</td>
<td align="center">
<strong>78% → 0.4%</strong><br>
<sub>장애 복구 시 429 비율<br>full jitter + 워커 동시성 제한</sub>
</td>
<td align="center">
<strong>80%↓</strong><br>
<sub>채점 제출 p95 레이턴시<br>커넥션 경합 해소</sub>
</td>
<td align="center">
<strong>2.7×</strong><br>
<sub>채점 레이턴시<br>문항별 가상 스레드 병렬 처리</sub>
</td>
</tr>
</table>

---

## 주요 문제 해결

- **채점 비동기 전환**: 음성 파일을 서버 인메모리로 동기 처리하는 구조에서, 장애 시 파일 유실 및 재시도 한계 발생. 파일 presigned S3 업로드 및 DB 큐 기반 워커가 재시도까지 맡는 비동기 구조로 전환 → 접수만 빠르게 동기 응답하고 외부 장애 흡수

  <details>
  <summary>자세히</summary>

  **문제 원인**
  - 음성 답변을 외부 API(STT, LLM)로 채점하며, 녹음 파일을 서버 메모리에 올려 요청 안에서 동기 처리
  - 재시도와 채점 결과가 요청에 묶여 있어 요청이 끊기면 함께 사라지는 구조
  - 서버 재시작이나 사용자 이탈 시 결과가 유실되고, 외부 API가 실패하면 사용자가 1분 넘는 답변을 다시 녹음

  **해결 과정**
  - 채점을 요청에서 떼어 비동기 작업으로 분리하고, 재시도와 결과 보존은 서버가 담당
  - 녹음 파일은 서버 디스크나 메모리 대신 S3에 두고 presigned URL로 직접 업로드해, 서버 재시작에도 남고 서버 메모리를 거치지 않게 처리
  - 채점 작업은 Kafka 같은 별도 큐 대신 DB 큐에 기록해 결과 저장과 상태 변경을 한 트랜잭션으로 처리, 워커가 문항 단위로 채점
  - 사용자가 응답을 기다리지 않게 되어, 재시도 한도를 횟수(3회) 대신 접수 후 시간 기준으로 늘려 지수 백오프
  - 실패를 기다리면 풀리는지로 나눠, 녹음 파일 문제는 즉시 실패, 429와 5xx, 타임아웃만 재시도
  - 몇 초 늦게 보여도 되는 채점 결과라, 연결을 서버에 유지해야 하는 SSE나 롱폴링 대신 요청마다 DB를 읽는 숏폴링으로 단순하게 응답
  - 테스트: 접수 직후와 채점 중 각각 서버 프로세스를 kill하고 재기동, 로컬에서 가짜 외부 API로 호출의 45%를 실패시키는 장애 주입

  **결과**
  - 채점을 비동기 작업으로 분리해 서버 재시작이나 사용자 이탈에도 채점 완료율 100%
  - 외부 API 일시 장애를 서버 재시도로 흡수해 사용자가 답변을 다시 녹음하지 않도록 개선 (45% 장애 주입 시 실패 문항: 재시도 3회 제한 22.8% → 시간 기준 재시도 0%)
  - 녹음 파일이 서버 메모리를 거치지 않아 힙 부담 감소 (Humongous GC 5분당 25회 → 3회)

  더 자세히: [블로그 글](https://velog.io/@niletheking/techdeepdive) / [설계 기록 (ADR)](docs/adr/0001-async-r2.md)

  </details>

- **재시도 폭주 방지**: 외부 API 장애 복구 시 재시도가 동시에 몰려 제공자 처리 한도를 넘기는 문제 발생. full jitter 지수 백오프로 재시도 시점을 분산하고 워커 동시성을 제한 → 외부 호출 87% 감소, 한도 초과(429) 비율 78% → 0.4%

  <details>
  <summary>자세히</summary>

  **문제 원인**
  - 채점을 비동기 워커로 옮긴 뒤, 외부 API(STT, LLM)가 실패하면 워커가 지수 백오프로 재시도하는 구조
  - 외부 API가 복구되는 순간 밀려 있던 재시도가 한꺼번에 몰려, 막 복구된 제공자에 다시 과부하를 줄 수 있는 구조
  - 과부하로 거절된 호출(429)이 다시 재시도를 부르며 복구 뒤에도 채점 완료가 늦어질 수 있음

  **해결 과정**
  - mock 외부 API로 60초 장애 후 복구(복구 직후 초당 10건 처리)를 재현해 재시도 방식 세 가지를 비교
    - 즉시 재시도: 복구 순간 처리량의 9배가 몰려 429 78%
    - 2배씩 늘린 대기 + 1초 jitter: 호출은 줄었지만 대기가 60초 상한에 붙어 모든 문항이 60초마다 같은 박자로 몰림
  - 대기 시간 자체가 아니라 대기 범위를 2배씩 늘리고, 실제 대기는 그 범위 안에서 무작위로 고르는 full jitter(AWS SDK 기본 재시도 방식)로 재시도 시점 분산
  - 워커 동시 처리 수를 제한해 한 번에 나가는 외부 호출 상한 고정

  <img src="docs/performance/2026-09-26/retry-storm.png" alt="재시도 방식별 초당 외부 호출" width="760">

  **결과**
  - 기존 백오프 대비 복구 후 채점 완료 270초 → 54초, 거절된 호출(429) 33% → 0.4%
  - 복구 직후 초당 최대 호출 39 → 12로 제공자가 처리할 수 있는 양 안에서 고르게 분산
  - 즉시 재시도 대비 외부 호출 87% 감소

  (mock 외부 API로 테스트: 콤보 30건, 60초 장애 후 복구, 복구 직후 초당 10건 처리)

  더 자세히: [실험 기록](docs/performance/2026-09-26/retry-storm.md)

  </details>

- **커넥션 경합 해소**: 문항 채점 중 재시도마다 대상 문항 정보를 DB에서 다시 조회해 발생한 커넥션 경합 발생. Caffeine 캐시 도입 → p95 레이턴시 80% 개선

  <details>
  <summary>자세히</summary>

  재시도/재전송 구조 도입 이후, 답변 채점마다 대상 문항을 DB에서 다시 조회하는 경로가 부하 상황에서 커넥션 경합을 일으켰습니다. 인메모리 캐시로 반복 조회를 제거할 기반을 만들고, 캐시 히트에도 커넥션을 선점하던 이전 `@Transactional` 경계를 함께 제거해 DB 조회·커넥션 획득이 없는 경로를 완성하여 **제출 p95 20.5s → 3.73s 개선** (동기 구조 시절, Mock, 500VU)

  </details>

- **문항 병렬 처리**: 3문항 채점 시 문항을 순차 처리해 외부 API 대기가 누적되는 문제 발생. 문항별 가상 스레드로 병렬 처리하여 레이턴시 2.7배 개선

  <details>
  <summary>자세히</summary>

  OPIc 콤보 2~3문항을 순차 채점 시 STT·LLM 외부 대기가 문항 수만큼 누적되어, `StructuredTaskScope`로 문항 간 병렬화(실패 시 나머지 취소)해 **기존 직렬 12,886ms → 병렬 실측 4,719ms 단축 (2.7배)**

  지금은 비동기 워커가 문항을 각각 독립된 작업으로 가상 스레드에서 처리합니다.

  </details>

- **코칭 리포트 역할 분리**: 코칭 리포트가 "오류가 거의 없는데 시제·어휘가 적절하지 않다"처럼 앞뒤 안 맞는 진단을 내는 문제. 패턴 집계를 LLM에 통째로 맡긴 게 원인이라, 답변 단위 판단은 LLM이, 집계와 문턱값, 그룹핑은 코드가 맡도록 분리
- **모니터링**: Prometheus, Grafana, Alertmanager 기반 모니터링 스택 구축 → 앱 골든 시그널과 외부 API 의존성, 비동기 워커 큐 등 대시보드에서 관측. 사용자 증상 기준 알림으로 장애 대응 체계 구성

  <img src="docs/images/grafana-dashboard.png" alt="Grafana 대시보드: 외부 API와 채점 워커" width="820">

  <sub>로컬 재현: 사용자 30명, 평소 → 외부 API 45% 장애 8분 → 복구. 장애 중 실패율과 429가 오르고 워커 재시도가 늘지만, 복구 후 밀린 문항이 처리되며 최종 실패 0</sub>

<details>
<summary><strong>비동기 전환 이전(동기 구조)의 기록</strong></summary>

- **디스크 I/O 병목 제거**: DB pool 확장·VT pinning 가설을 1KB 격리 실험과 JFR로 기각/특정한 뒤 톰캣 멀티파트 임시파일 쓰기가 원인임을 확인, 멀티파트 임계치를 올려 디스크 쓰기를 제거 — **RPS 96→652, Avg Latency 1,100ms→249ms** (2026-04, Mock STT/LLM, 단일 실행). 당시 함께 넣은 InputStream 릴레이는 6월 재시도 요구사항으로 되돌림 — 아래 접기 참고

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

- **외부 API 장애 대응**: 외부 STT/LLM 일시 장애로 인한 녹음 유실을, 서버 지수 백오프 3회 재시도 + 실패 문항만 재전송하는 구조로 방지 (문제 본문은 서버가 통제해 재전송 시에도 LLM 입력을 신뢰) (이후 비동기 전환으로 워커의 시간 기준 재시도로 대체)

</details>

---

## 기술 스택

| | |
|---|---|
| **Language / Runtime** | Java 21, Virtual Threads |
| **Framework** | Spring Boot 3.4, Spring AI, Spring Security OAuth2 |
| **AI / STT** | Groq Whisper (STT), Gemini 3.5 Flash-Lite (채점, 코칭 작성, 태깅 — 무료 등급). 공급자·모델은 `application-<공급자>.yml`로 분리해 `LLM_PROVIDER` 하나로 바꾼다 |
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
