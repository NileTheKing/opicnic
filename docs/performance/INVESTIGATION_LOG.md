# 🕵️‍♂️ Opicnic 성능 최적화 수사 일지 (Investigation Log)

본 문서는 성능 최적화 과정에서 발생한 이슈와 가설 검증, 최종 해결 과정을 기록하여 기술적 부채가 발생하는 것을 방지하기 위함임.

---

## 1. 사건 발생: 500 VU 부하 시 서버 사망
- **현상**: 동시 접속자 500명 도달 시 응답 지연이 1.1s로 고정되며 최종적으로 프로세스 종료.
- **분석 도구**: k6 (http_req_duration 지표).
- **식별된 병목**: `AsyncConfig`의 `FixedThreadPool(10)`. 가상 스레드 환경에서도 내부 비즈니스 로직이 한정된 플랫폼 스레드 풀에 갇혀 지연 발생.

## 2. 가상 스레드 엔진 전환
- **조치**: `taskExecutor`를 `VirtualThreadPerTaskExecutor`로 교체.
- **결과**: RPS 96 ➡️ 242 (2.5배 향상). 
- **교훈**: 가상 스레드 효과를 보려면 전체 파이프라인의 실행 엔진이 가상 스레드 친화적이어야 함.

## 3. 데이터 정합성 함정 (Enum Mismatch)
- **현상**: 실 DB 조회 추가 후 성공률 0%로 급락.
- **추론**: DB 커넥션 부족을 의심했으나 풀 확장(50) 후에도 증상 동일.
- **범인**: `server_before_final.log` 분석 결과, 테스트 데이터의 Enum 값(`topic=Introduction`)이 서버 정의와 불일치하여 `IllegalArgumentException` 발생.
- **해결**: 실제 적재 데이터(`MOVIE_WATCHING`)로 시나리오 교정.

## 4. JFR 분석과 Disk I/O의 실체
- **현상**: 데이터 교정 후에도 p95 지연 시간이 1.13s에서 요지부동.
- **분석**: JFR(Java Flight Recorder) 가동 및 `jdk.ObjectAllocationSample` 추출.
- **발견**: 대규모 `byte[]` 할당과 `Disk I/O Wait` 확인. 톰캣이 10KB 초과 파일을 디스크에 쓰는 기본 동작이 진짜 원인임을 과학적으로 입증.

## 5. 최종 해결: In-Memory 스트리밍 & 구조적 병렬 처리
- **조치**: 
  1. `file-size-threshold: 2MB` 설정으로 디스크 쓰기 제거.
  2. `InputStream` 직접 릴레이 구조로 프레임워크 버퍼링 우회.
  3. `CompletableFuture`를 제거하고 `invokeAll()` 기반의 동기 스타일 병렬 처리로 리팩터링.
- **성과**: **p95 Latency 1,130ms ➡️ 238ms (약 80% 단축)**.
