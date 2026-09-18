# 성능 테스트 재현 가이드 (Reproduction Guide)

OPIcnic 백엔드의 성능 벤치마크를 동일한 환경에서 수행하기 위한 절차입니다.

---

## 1. 사전 준비
- **인프라**: MySQL만 있으면 된다 (STT/LLM은 mock. 2026-04에 쓰던 Python STT 워커는 이후 Groq Whisper로 대체됨). 로컬 3306이 다른 프로젝트에 점유돼 있으면 3307 임시 컨테이너 + `SPRING_DATASOURCE_URL` 환경변수
- **데이터**: `DataInitializer.java`를 통해 `MOVIE_WATCHING` / `LEVEL_3` 데이터가 적재되어 있어야 함

## 2. 서버 실행
가상 스레드 옵션과 메모리 설정을 포함하여 실행합니다. `SPRING_PROFILES_ACTIVE=dev`가 있어야
로그인 세션 없이 attempt를 시작하는 `POST /api/practice-attempts/start`(k6 스크립트가 사용)가 열립니다.
```bash
SPRING_PROFILES_ACTIVE=dev \
LLM_ENABLED=false \
STT_ENABLED=false \
JAVA_TOOL_OPTIONS="-Xms2g -Xmx2g -Djdk.tracePinnedThreads=short" \
./gradlew bootRun
```

> **`JAVA_OPTS`가 아니라 `JAVA_TOOL_OPTIONS`다.** `gradlew`의 `JAVA_OPTS`는 Gradle 클라이언트 JVM에만 적용되고
> `bootRun`이 포크하는 앱 JVM에는 전달되지 않는다(`build.gradle`의 `jvmArgs`만 받음). 2026-09-11까지 이 가이드가
> `JAVA_OPTS`로 적혀 있어서 앱은 기본 힙(시스템 메모리 1/4)으로 돌고 있었다. 기동 후 반드시 확인할 것:
> ```bash
> jcmd $(pgrep -f OpicnicApplication) VM.flags | grep -o "MaxHeapSize=[0-9]*"
> ```
> 상세: `2026-09-11-gc-pressure-finding.md` 발견 1.


## 3. 부하 테스트 실행 (k6)
```bash
# 500 VU, 30s 테스트 실행
k6 run --vus 500 --duration 30s scripts/load-test.js
```

### S1 (모의고사 15문항)
`SPRING_PROFILES_ACTIVE=dev`에서 `POST /api/practice-attempts/start-mock`으로 로그인 없이 15문항 모의고사
attempt를 시작할 수 있다(응답 형식은 `/start`와 동일: `attemptId`/`questionIndexes`/`questionCount`).

### S2 (외부 API 실패 주입)
mock(`STT_ENABLED=false`/`LLM_ENABLED=false`) 상태에서 아래 4개 환경변수로 429/5xx 실패율을 주입할 수 있다
(기본값 0 — 안 주면 기존처럼 항상 성공). LLM 쪽은 채점(`getOpicFeedback`)에만 적용되고 태깅은 그대로다.
```bash
STT_MOCK_429_RATE=0.2   # STT 429 실패율 (0.0~1.0)
STT_MOCK_5XX_RATE=0.1   # STT 5xx 실패율
LLM_MOCK_429_RATE=0.2   # 채점 LLM 429 실패율
LLM_MOCK_5XX_RATE=0.1   # 채점 LLM 5xx 실패율
```
호출 횟수는 `[MOCK] ... 실패 주입 (429)` / `(503)` 로그 라인을 grep해서 센다.

## 4. 정밀 프로파일링 (JFR)
병목 분석 필요 시 실행 중인 JVM에 JFR 녹화를 명령합니다.
```bash
# JFR 시작 (PID 확인 필요)
jcmd <PID> JFR.start duration=60s filename=recording.jfr settings=profile

# 특정 이벤트(Object Allocation) 데이터 추출
jfr print --events jdk.ObjectAllocationSample recording.jfr | head -n 100
```

## 5. 성능 메트릭 기준
- **RPS**: 600 req/s 이상 (최적화 상태)
- **p95 Latency**: 300ms 이하 (애플리케이션 계층 지연 제거 기준)
