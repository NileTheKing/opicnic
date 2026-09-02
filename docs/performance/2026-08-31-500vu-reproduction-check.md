# OPicnic 개발일지 - 500VU 재현 검증 (byte[] 재도입 이후 성능 재측정)

## 날짜

2026-08-31

---

## 1. 배경

STT/LLM 재시도 기능 때문에 `InputStream` 릴레이 구조를 `byte[]` 버퍼링으로 되돌린 뒤
(`docs/multipart-bytearray-cleanup-spec-2026-08-21.md`), 4월 벤치마크(`RPS 96→652`,
`virtual-threads-benchmarking.md`)와 같은 조건에서 지금 코드가 여전히 그 수치를 내는지 확인 필요.

Git SHA: `8e70a543fd3f0a371e99e1779fa3bab11a4214b3` (테스트 중 코드/설정 무수정)

## 2. 실행 조건

- `SPRING_PROFILES_ACTIVE=dev LLM_ENABLED=false STT_ENABLED=false JAVA_OPTS="-Xms2g -Xmx2g -Djdk.tracePinnedThreads=short" ./gradlew bootRun` (호스트 맥 위 직접 실행, Docker 아님 — 4월/오늘 동일)
- MySQL은 `docker-compose.yml`의 3306 포트를 다른 프로젝트(`axon-mysql`)가 점유 중이라, 독립 컨테이너(`mysql:8.0`, 포트 3307)로 대체 — 앱 설정 파일은 무수정, env var로만 접속 포트 변경
- `k6 run --vus 500 --duration 30s`, `test_audio.webm` 1,048,576 bytes
- STT/LLM mock delay: 0ms (`STT_MOCK_DELAY_MS`/`LLM_MOCK_DELAY_MS` 미설정 — 4월 코드에는 이 개념 자체가 없었으므로 동일 조건)
- 머신: Apple M2 8코어 16GB, macOS 15.6.1 — 4월 당시 스펙은 로그에 기록이 없어 완전 동일 확정은 불가(같은 로컬 경로/계정/JDK 버전이라 같은 랩탑일 가능성 높음)

## 3. 1차: 현재 `scripts/load-test.js` 그대로 (요청당 콤보 전체 파일 제출)

| 지표 | 값 |
|---|---|
| `http_reqs` | 84.15/s |
| `answers_duration` p50/p95 | 887ms / 18.69s |
| `error_rate` | 2.29% |
| 서버 `OutOfMemoryError` | **87회** |
| 클라이언트 소켓 고갈(`no buffer space available`) | 44회 |

**원인**: 현재 스크립트는 `/start`(attempt 생성) → `/answers`에 **콤보 전체 파일(질문 2~3개)**을 한 번에 제출한다.
서버 로그 확인 결과 요청당 파일 2~3개가 동시에 `byte[]`로 버퍼링됨 — 500VU가 동시에 이러면
`-Xmx2g` 힙을 소진해 실제 OOM 발생. 서버 프로세스 자체는 죽지 않고 회복했다(`csrf` 200 유지).

## 4. 원본(4월) 조건 재확인

`git show 6c5c761:scripts/load-test.js`로 확인한 원본 스크립트는:
- 요청당 **파일 1개**만 제출 (`http.file(binFile, 'test_audio.wav')`, `questions[0]` 하나)
- `/practice/combo/feedback` (지금은 삭제된 엔드포인트) — **클라이언트가 보낸 질문 내용을 그대로 신뢰**,
  서버 DB 재검증 단계 없음
- CSRF 핸드셰이크 없음 (당시 `SPRING_PROFILES_ACTIVE` 미설정, dev profile 분리는 6/6에 추가됨)

즉 오늘 1차의 OOM은 "byte[] 구조 자체의 퇴행"이 아니라 **요청당 페이로드가 원본보다 2~3배 무거워진
테스트 시나리오 변화**가 주 원인이었다.

## 5. 2차: 파일 개수만 원본과 동일하게 맞춘 재측정

원본 엔드포인트는 이미 삭제되어 그대로 재현 불가 — 현재 아키텍처(`/start`+`/answers`, CSRF 포함,
서버 측 질문 재검증 포함)는 유지하고 **파일 개수만 1개로 맞춘 임시 k6 스크립트**로 재측정.
(임시 스크립트: 세션 스크래치패드에만 존재, 리포지토리에는 커밋 안 함)

| 지표 | 값 |
|---|---|
| `http_reqs` | **1382.4/s** |
| `iterations`(완전한 사이클) | 460.8/s |
| `answers_duration` p50/p95 | 6.04ms / **72.08ms** |
| `error_rate` | **0.00%** |
| 서버 `OutOfMemoryError` | **0회** |
| 소켓 고갈 | 0회 |

## 6. 4월(652 RPS) vs 오늘 2차(1382 RPS) — 왜 이렇게 차이 나나

파일 개수·mock delay(0ms)·VU수·시간을 맞춘 뒤에도 오늘 2차가 4월보다 훨씬 높게 나온 이유는
그 사이(6월)에 **디스크 I/O와는 별개의 병목**이 하나 더 잡혔기 때문:

1. 4월 벤치마크는 클라이언트가 보낸 질문 내용을 그대로 신뢰하는 구조라 DB 재조회가 없었음(원천적으로 이 병목이 존재할 수 없었음).
2. 6월에 보안 강화(`restoreQuestionsForIndexes` — 서버가 DB에서 질문을 재검증)를 도입하면서 500VU 부하테스트(6/6)에서 HikariCP 평균 대기 6.8초, p95 20.5초 발견 (`docs/local/2026-06-11-question-cache-load-test.md`).
3. `ConcurrentHashMap` 질문 캐시 추가 → 거의 개선 안 됨(20.5s→20.17s) → Prometheus HikariCP 지표 재확인 → **캐시 히트해도 `@Transactional(readOnly=true)`가 커넥션을 미리 꺼내가는 유령 버그** 발견 → 제거.
4. 결과: `start p95: 16.69s → 20ms`.

즉 4월(디스크 I/O 제거) + 6월(DB 커넥션 경쟁 제거) + 오늘(파일 개수 통제)이 누적된 결과이며,
오늘 코드는 4월 대비 서버 측 질문 재검증까지 추가로 수행하면서도 더 빠르다.

## 7. 비교 시 주의할 것

- **`answers_duration`(오늘 2차, 72ms) vs 6월 문서의 "mock delay 3.7초 수렴" 서술은 같은 측정이 아니다.**
  오늘 2차는 `STT_MOCK_DELAY_MS`/`LLM_MOCK_DELAY_MS`를 0으로(미설정) 돌렸고, 6월 문서는 실제 Groq
  응답시간을 흉내낸 2600ms/1100ms 조건이었다. 이 둘을 나란히 쓰면 안 됨 — 대외 자료(레주메 등)에는
  **RPS만** 쓰는 것으로 정리.
- 4월과 오늘 2차의 RPS(652 vs 1382)는 둘 다 delay=0 조건이라 비교 가능.
- 4월과 오늘은 완전히 동일한 코드 경로가 아니다 — 오늘 쪽이 CSRF 발급 + attempt 생성 + DB 질문
  재검증까지 추가로 수행한다. 즉 "더 많은 일을 하는데도 더 빠르다"는 비교이지, "같은 일을 더
  빠르게 한다"는 비교가 아니다.

## 8. 정리

- 수정한 리포지토리 파일 없음(1차/2차 모두 코드·설정 무수정, 임시 k6 스크립트는 세션 스크래치패드에만 존재)
- 테스트용 MySQL 컨테이너(포트 3307)와 두 번의 `bootRun` 프로세스 모두 테스트 후 정리
- 기존에 떠 있던 다른 프로젝트 컨테이너(`axon-mysql`, `axon-redis`)는 건드리지 않음
