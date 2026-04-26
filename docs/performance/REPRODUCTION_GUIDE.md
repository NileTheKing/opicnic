# 성능 테스트 재현 가이드 (Reproduction Guide)

Opicnic 백엔드의 성능 벤치마크를 동일한 환경에서 수행하기 위한 절차입니다.

---

## 1. 사전 준비
- **인프라**: `docker-compose up -d`를 통해 MySQL 및 STT Worker가 실행 중이어야 함
- **데이터**: `DataInitializer.java`를 통해 `MOVIE_WATCHING` / `LEVEL_3` 데이터가 적재되어 있어야 함

## 2. 서버 실행
가상 스레드 옵션과 메모리 설정을 포함하여 실행합니다.
```bash
AI_GEMINI_ENABLED=false \
STT_ENABLED=false \
JAVA_OPTS="-Xms2g -Xmx2g -Djdk.tracePinnedThreads=short" \
./gradlew bootRun
```

## 3. 부하 테스트 실행 (k6)
```bash
# 500 VU, 30s 테스트 실행
k6 run --vus 500 --duration 30s scripts/load-test.js
```

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
