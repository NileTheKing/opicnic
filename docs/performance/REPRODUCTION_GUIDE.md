# 🚀 성능 테스트 재현 가이드 (Reproduction Guide)

이 문서는 Opicnic의 성능 벤치마크를 동일한 환경에서 다시 수행하기 위한 가이드임.

---

## 1. 사전 준비
- **도커 실행**: `docker-compose up -d` (MySQL, STT Worker 가동)
- **테스트 데이터**: `DataInitializer.java`를 통해 `MOVIE_WATCHING` / `LEVEL_3` 데이터가 인서트되어 있어야 함.

## 2. 서버 실행 (최적화 모드)
가상 스레드 피닝 추적 및 힙 메모리 2GB 설정을 포함하여 실행함.
```bash
AI_GEMINI_ENABLED=false \
STT_ENABLED=false \
JAVA_OPTS="-Xms2g -Xmx2g -Djdk.tracePinnedThreads=short" \
./gradlew bootRun
```

## 3. 부하 테스트 실행 (k6)
```bash
# 500명 동시 접속, 30초 테스트
k6 run --vus 500 --duration 30s scripts/load-test.js
```

## 4. 정밀 프로파일링 (JFR)
병목 의심 시 아래 명령어로 JVM 내부를 녹화함.
```bash
# 실행 중인 서버에 JFR 시작 (PID 확인 필요)
jcmd <PID> JFR.start duration=60s filename=recording.jfr settings=profile

# 결과 요약 보기
jfr summary recording.jfr

# 특정 이벤트(메모리 할당) 보기
jfr print --events jdk.ObjectAllocationSample recording.jfr | head -n 100
```

## 5. 지표 해석 기준
- **http_req_duration (avg)**: 300ms 이하면 정상.
- **http_req_duration (p95)**: 800ms 이상이면 Disk I/O 병목 재발 의심.
- **RPS**: 600 req/s 이상 유지되어야 최적화 상태임.
