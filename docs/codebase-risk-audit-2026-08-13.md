# 코드베이스 리스크 감사 (면접관 관점)

> 기준 커밋: `b1168e8` (`main`)  
> 감사일: 2026-08-13  
> 범위: 백엔드 소스, 설정, 배포, 테스트, 문서  
> 원칙: **발견과 근거 기록만 수행했다. 애플리케이션 코드는 수정하지 않았다.**
>
> **2026-08-13~19 후속 수정**: `CORE-01`, `DOMAIN-01`, `ADMIN-01`, `SEC-03`, `SCORE-01`, `CACHE-01`, `SEC-04`, `SEC-01`, `SEC-05`, `SEC-06`, `SEC-07`, `API-02`, `DATA-02`, `PERF-01`, `DOC-01` 완료. `SEC-02`, `COST-01`은 즉시 가능한 부분만 완료했다. 상세 이력은 [`docs/CHANGELOG.md`](CHANGELOG.md) 참고.
>
> **2026-08-20 재리뷰**: `DATA-01`, `SCORE-02`, `API-01`, `ADMIN-02`, `AI-01`은 부분 완료, `TEST-02`는 실행 경로는 복구됐지만 dev rate limit 때문에 성능 threshold가 실패하는 상태로 판정했다. 후속 구현의 source of truth는 [`audit-followup-spec-2026-08-20.md`](audit-followup-spec-2026-08-20.md)다.
>
> **2026-08-20 FU 후속 수정**: `FU-02`(SCORE-02), `FU-03`(TEST-02), `FU-04`(API-01), `FU-06`(AI-01) 완료. `FU-01`(DATA-01, DB marker 기반 멱등성)과 `FU-05`(ADMIN-02, 모든 진입점 조립 가능성)는 이번 배치에서 제외 — 여전히 부분 완료 상태.
>
> 나머지 P1(`TEST-01`)은 보류, P3(`API-03`, `OPS-01`, `DB-01`, `DESIGN-01`)는 미착수다.

## 1. 어떻게 봤는가

면접관이 저장소를 짧게 읽거나 정적 분석/의존성 스캔을 돌렸을 때 바로 드러나는 항목을 우선했다.

- API/HTTP: 인증·인가, 상태 코드, 요청 검증, 예외 응답, 멱등성
- 보안 기본기: 비밀정보, CSRF, 관리 경로, 업로드, 의존성 CVE, 운영 엔드포인트
- 데이터 기본기: 트랜잭션, 유일성, 동시성, JPA 관계, 캐시 일관성
- 도메인 정확성: 모의고사 구성, 채점/가중치, 돌발 주제 정책
- 검증 가능성: 테스트 격리, CI/CD gate, 성능 수치 재현 스크립트
- OOP/설계는 실제 오동작으로 이어지는 책임 혼재나 이중 source of truth만 기록했다. 단순 스타일 취향은 제외했다.

심각도는 다음 기준이다.

| 등급 | 의미 |
|---|---|
| P0 | 현재 외부 악용/대규모 데이터 손실이 확인된 상태 |
| P1 | 면접·라이브 데모 전에 확인해야 하는 핵심 기능 장애, 권한/비밀/비용/무결성 문제 |
| P2 | 명백한 오동작 또는 운영·API 기본기 결함 |
| P3 | 바로 장애는 아니지만 면접관이 유지보수성·설계 일관성을 물을 만한 냄새 |

P0는 발견하지 못했다. 아래 P1은 “언젠가 개선” 수준이 아니라, 현재 코드 설명보다 먼저 사실관계를 확인해야 하는 항목이다.

## 2. 먼저 볼 P1 요약

| ID | 발견 | 직접 영향 | 확신 |
|---|---|---|---|
| SEC-01 | ✅ 완료(08-13) 일반 USER도 관리자 UI/API 접근 가능 | 전역 문제은행 생성·수정·삭제 | 매우 높음 |
| SEC-02 | 🟡 부분 완료(08-13) 평문 자격증명 후보가 Git과 빌드 리소스에 남음 | credential scan 즉시 탐지, 유출 취급 필요 | 매우 높음 |
| SEC-03 | ✅ 완료(08-13) `.dockerignore` 없이 실제 `.env`를 build context에 포함 | Docker daemon/cache/build stage로 비밀 전송 | 매우 높음 |
| SEC-04 | ✅ 완료(08-13) 런타임 Tomcat 10.1.39에 공개된 Important CVE 포함 | 공개 multipart 경로를 통한 DoS 등 | 매우 높음 |
| COST-01 | 🟡 부분 완료(08-13) 비용 API limiter가 죽은 경로에만 연결됨 | Groq 비용·쿼터·서버 자원 무제한 소비 | 매우 높음 |
| CORE-01 | ✅ 완료(08-13) 모의고사 자기소개 문항의 `questionType=null` 미처리 | 정상적인 15문항 모의고사 완료 불가 | 매우 높음 |
| DATA-01 | 🟡 부분 완료(08-20 재리뷰) finalize가 DB 기준으로 원자적·멱등적이지 않음 | 부분 저장·중복 피드백·통계 오염 | 매우 높음 |
| SCORE-01 | ✅ 완료(08-13) 최신순 가중 평균을 거꾸로 계산 | 오래된 실력을 최근 실력보다 크게 반영 | 매우 높음 |
| SCORE-02 | ✅ 완료(08-20, FU-02) 짧은 롤플레이 응답도 평가 제외 적용 | 롤플레이 연습 시 등급이 구조적으로 하락 | 높음 |
| TEST-01 | 안전하고 신뢰할 수 있는 전체 테스트/배포 gate 부재 | 위 회귀들이 `main` 배포 전에 차단되지 않음 | 매우 높음 |
| SEC-05 | ✅ 완료(08-13) 공개 Grafana가 비밀번호 누락 시 `admin`으로 fallback | 설정 누락 배포에서 관리 UI 탈취 가능 | 높음 |
| ADMIN-01 | ✅ 완료(08-13) 관리자 목록이 존재하지 않는 필드를 렌더링 | 목록 화면 500 가능성 | 매우 높음 |

## 3. P1 상세

### SEC-01. 관리자 권한이 아니라 로그인 여부만 검사한다

> **✅ 완료 (2026-08-13)**: `SecurityConfig`의 matcher를 `.requestMatchers("/admin/**", "/api/admin/**").hasAuthority("ADMIN")`으로 변경(기존엔 `/api/admin/**`만 `.authenticated()`였고 `/admin/**` 뷰 라우트는 아예 매칭이 없어 `anyRequest().authenticated()`로 흘러 로그인만 하면 통과했음). `CustomOAuth2UserService`가 이미 `member.getRole().name()`을 authority로 부여하고 있어 별도 인프라 추가 없이 matcher만 고치면 됐음. 테스트: `SecurityConfigAdminAccessTest`(비인증 리다이렉트, `USER` 403, `ADMIN` 200 — 뷰/API 둘 다).
> **⚠️ 운영 후속 조치 필요**: 저장소 전체에 `Role.ADMIN`을 실제로 부여하는 코드 경로가 없다(`CustomOAuth2UserService`는 신규 회원을 항상 `Role.USER`로 생성). 즉 이 수정을 배포하면 **DB에서 직접 role을 `ADMIN`으로 바꾸기 전까지는 누구도(운영자 본인 포함) `/admin` 화면에 접근할 수 없다.** 로컬/운영 DB 각각에서 `UPDATE member SET role='ADMIN' WHERE ...`를 수동 실행해야 한다.

**근거**

- `src/main/java/com/opicnic/opicnic/config/SecurityConfig.java:29-31`
  - `/api/admin/**`는 `authenticated()`만 요구한다.
  - `/admin/**`도 별도 role 규칙이 없어 `anyRequest().authenticated()`에 걸린다.
- `src/main/java/com/opicnic/opicnic/service/CustomOAuth2UserService.java:58-75`
  - 신규 가입자는 `Role.USER`이고 authority도 `USER`다.
- `src/main/java/com/opicnic/opicnic/controller/AdminQuestionSetApiController.java:25-47`
  - 생성·수정·삭제 메서드에 추가 권한 검사가 없다.

**영향**

카카오로 로그인한 일반 회원이 관리자 화면을 보고 전역 `QuestionSet`을 생성·변경·소프트 삭제할 수 있다. `Role.ADMIN` enum은 존재하지만 접근 제어에는 사용되지 않는다. 면접관이 Spring Security 설정을 보면 가장 먼저 잡힐 가능성이 높다.

**최소 확인 방법**

일반 USER 세션으로 `POST /api/admin/question-sets`를 호출해 403이 아닌 성공 응답과 DB insert 여부를 확인한다. 운영 DB에서는 수행하지 않았다.

---

### SEC-02. 평문 자격증명 후보가 Git 이력과 빌드 산출물에 포함된다

> **🟡 부분 완료 (2026-08-13)**: `application.properties.old`를 `git rm --cached`로 추적 대상에서 제거, 빌드 산출물(`build/resources/main/`)에도 clean build로 재확인해 안 남는 것 확인. `.gitignore`에 `*.properties.old`/`*.properties.bak`/`*.yml.old`/`*.yml.bak` 패턴 추가해 재발 방지.
> **아직 안 한 것**: 이 파일에 있던 카카오 OAuth client-secret 재발급(회전) — 카카오 개발자 콘솔 접근 권한이 필요해 사용자가 직접 해야 함. 로컬 DB 비밀번호는 이미 다른 사고(2026-07-15 랜섬웨어) 대응 과정에서 회전됨. **과거 Git 커밋 히스토리(2025-03-30 최초 커밋부터)에는 여전히 값이 남아있음** — 저장소가 처음부터 public이었고, 히스토리 재작성(`git filter-repo`)은 이번엔 하지 않기로 함(개인 프로젝트 규모 대비 리스크/수고 불균형 판단, credential 재발급으로 값 자체를 무효화하는 쪽을 택함).

**근거**

- 추적 중인 `src/main/resources/application.properties.old:11`에 평문 DB 비밀번호가 있다.
- 같은 파일 `:26`에 주석 처리된 Kakao client secret 형태의 값이 있다.
- 이 값들은 Git 이력에도 남아 있다(`git log --follow -S...`로 최초 커밋까지 확인).
- `src/main/resources` 아래 파일이라 `processResources`가 그대로 복사한다. 실제로 `build/resources/main/application.properties.old` 포함을 확인했다.

**영향**

주석이나 `.old` 확장자는 secret scanner와 빌드 패키징을 피하지 못한다. 값이 과거/개발용이어도 저장소에 들어온 순간 노출된 자격증명으로 취급해야 한다. 실제 자격증명인지 확인 후 회전 여부를 판단해야 하며, 보고서에는 값을 재기록하지 않는다.

---

### SEC-03. Docker 빌드가 `.env`와 Git 이력을 build context로 보낸다

> **✅ 완료 (2026-08-13)**: `.dockerignore` 추가(`.env`, `.git`, 빌드 산출물, IDE 설정, `docs/local/` 등). 더미 Dockerfile(`COPY . .`만 수행)로 실제 build context에 `.env`/`.git`이 안 들어가는 것을 직접 빌드해 확인.

**근거**

- 저장소에 `.dockerignore`가 없다.
- `Dockerfile:4`는 `COPY . .`다.
- `deploy.sh:4-10`은 실제 비밀이 든 `.env` 존재를 배포 전제 조건으로 삼는다.
- `deploy.sh:18`은 그 상태에서 `docker compose ... up -d --build`를 실행한다.

**영향**

Git에서는 `.env`를 ignore하지만 Docker build context에서는 제외되지 않는다. 따라서 `.env`와 `.git`이 Docker daemon/builder로 전송되고 build stage `/app` 및 캐시에 들어간다. 최종 multi-stage 이미지에 파일이 복사되지 않는 것과 build context·cache 노출은 별개다.

**확인 제한**

실제 `.env` 내용은 열어보지 않았고 Docker build도 실행하지 않았다. 파일 선택 규칙만으로 포함 여부가 결정되므로 정적 근거는 충분하다.

---

### SEC-04. 현재 런타임에 알려진 Important 등급 Tomcat 취약점이 포함된다

> **✅ 완료 (2026-08-13)**: Spring Boot `3.4.4` → `3.4.13`(같은 3.4.x 라인 내 최신 패치)로 업그레이드, Tomcat이 `10.1.39` → `10.1.50`으로 따라 올라감. Apache 공식 advisory 기준 CVE-2025-48988/48976(10.1.42에서 수정)과 CVE-2025-31650(10.1.40에서 수정) 모두 해소 범위. `dependencyInsight`로 실제 반영 버전 확인, 전체 유닛/컨트롤러 테스트 통과 확인(로컬 DB가 필요한 `FullPipelineEndToEndTest`/`ManualSeedRunner`는 이번 변경과 무관하게 로컬 MySQL 미실행으로 원래도 실패하는 테스트 — TEST-01 참고).

**근거**

- `build.gradle:3`은 Spring Boot `3.4.4`다.
- `./gradlew dependencyInsight --dependency tomcat-embed-core --configuration runtimeClasspath` 결과는 `tomcat-embed-core:10.1.39`다.
- Apache 공식 공지상 다음 범위에 포함된다.
  - CVE-2025-48988: multipart part 수를 이용한 메모리 DoS, 10.1.0-M1~10.1.41 영향
  - CVE-2025-48976: multipart part header를 이용한 메모리 DoS, 10.1.0-M1~10.1.41 영향
  - CVE-2025-31650: 잘못된 HTTP priority header에 의한 메모리 누수 DoS, 10.1.10~10.1.39 영향
  - 출처: [Apache Tomcat 10 security advisories](https://tomcat.apache.org/security-10.html)
- `src/main/java/com/opicnic/opicnic/config/SecurityConfig.java:30`은 `/api/**`를 공개한다.
- `PracticeAttemptApiController.java:98-119`는 attempt/owner 검증 전에 multipart parameter/part를 파싱한다.

**영향**

일반적인 dependency scanner가 바로 잡는 항목이고, 이 프로젝트에는 실제 공개 multipart 파싱 경로가 있어 적어도 multipart DoS 두 건은 공격면과 맞닿아 있다.

---

### COST-01. 비용 API limiter가 실제 경로에 적용되지 않고, 한 요청으로 작업 수를 증폭할 수 있다

> **🟡 부분 완료 (2026-08-13)**: "즉시 가능" 방어선은 모두 적용. **미완료**(정책 결정 필요)는 그대로 남김.
> - Limiter 경로를 죽은 경로(`/practice/combo/feedback`) → 실제 비용 경로(`/api/practice-attempts/*/answers`, `/answers/retry`, `/analytics/coaching`)로 교체. `/analytics/coaching`은 GET(조회)과 POST(리포트 생성)를 같은 경로로 공유해서, `RateLimitInterceptor`가 POST만 소비하도록 수정(GET까지 소비하면 페이지 조회 몇 번으로 한도 소진됨).
> - **한도를 "요청 수" 대신 "문항 수" 기준으로 재정의**: 모의고사가 15문항을 한 요청에 몰아 제출하는 구조라, 요청 수 기준 10회/시간은 사실상 최대 150문항/시간을 허용하는 셈이었다. 시간당 15문항으로 변경, `answers`/`answers/retry` 제출 시 실제 questionIndexes 개수만큼(자기소개는 채점 대상이 아니므로 미포함 — PC-01과 같은 규칙) 소비.
> - **소비 시점을 인터셉터에서 컨트롤러로 이동**: 최초 구현은 `RateLimitInterceptor`(컨트롤러 진입 전)에서 먼저 소비했는데, 그러면 중복 index·대용량 파일 등으로 400 거부될 요청도 한도를 미리 깎아먹는 문제가 있었다(리뷰로 발견). 버킷 로직을 `RateLimiterService`로 분리하고, `/api/practice-attempts/*/answers[/retry]`는 인터셉터에서 걸지 않도록 변경 — `PracticeAttemptApiController.processSubmission()`이 모든 입력 검증을 통과한 뒤, 실제 LLM 호출 직전에 `RateLimiterService.tryConsume()`을 직접 호출한다. 검증 실패 = 비용 없음 = 한도 소비 없음이 되도록 정렬. `/analytics/coaching`처럼 검증이 단순한 경로는 그대로 인터셉터에서 요청 1건당 1 소비.
> - `PracticeAttemptApiController.processSubmission()`에 요청 증폭 방어 추가: questionIndexes의 null·중복 거부, attempt 총 문항 수 초과 거부, **이미 성공 처리된 문항 재제출 거부**(세션의 기존 성공 결과와 대조), 답변 파일 15MB 초과 거부, `audio/webm` 외 content-type 거부.
> - 비로그인 사용자는 이 경로에 실질적으로 도달할 수 없음을 확인(연습 시작 라우트가 전부 로그인 필수라 attemptId 자체를 발급받을 수 없고, 발급된 attempt를 비로그인으로 두드려도 컨트롤러의 소유권 검사가 401 처리) — `RateLimiterService`의 "anonymous" 공유 버킷은 정상 플로우에서 도달하지 않는 죽은 방어선이라는 점만 기록해두고 이번엔 손대지 않음.
> - 테스트: `PracticeAttemptApiControllerCostGuardTest`(6개 방어선 각각 정확한 사유로 400 되는지 메시지까지 확인 + 검증 실패 시 `RateLimiterService`가 아예 호출 안 되는지 + 문항 수/자기소개 제외 계산 + 429 케이스), `RateLimitInterceptorTest`(GET 미소비, `/analytics/coaching` 요청 1건당 1 소비).
> - **사람이 정할 예산(미완료)**: IP/member/attempt/전역 provider quota 각각의 제한, 답변 1문항이 재시도로 얼마까지 증폭 가능한지, 429/timeout 시 retry budget과 사용자 안내, anonymous 공유 버킷을 IP 기준으로 나눌지 여부.

**근거 A — limiter가 죽은 경로를 가리킴**

- `src/main/java/com/opicnic/opicnic/config/WebConfig.java:16-17`
  - limiter 대상은 `/practice/combo/feedback`이다.
- 저장소 전체에 이 요청 매핑은 없다.
- 실제 비용 경로는 `PracticeAttemptApiController.java:55-66`의
  - `/api/practice-attempts/{attemptId}/answers`
  - `/api/practice-attempts/{attemptId}/answers/retry`
- 코칭 LLM 경로 `CoachingController.java:74-79`에도 제한이 없다.

**근거 B — 요청 증폭 입력을 허용함**

- `PracticeAttemptApiController.java:98-123`은 index가 비어 있지 않고 범위 안이며 파일 수가 같은지만 검사한다.
- duplicate/null index, 총 항목 수, MIME/확장자에 대한 서버 검증이 없다.
- `PracticeAttemptService.java:47-69`는 `[0,0,...]` 같은 중복 index를 그대로 중복 문항으로 복원한다.
- `FeedbackService.java:55-98`은 항목마다 task를 fork하고 `readAllBytes()` 후 STT + 채점 LLM + 태깅 LLM을 호출한다.
- `application.yml:53-58`과 `docker/nginx/nginx.conf.template:5`는 요청 한도를 150MB로 둔다.

**영향**

일반 사용자 하나가 1문항 attempt에 중복 index N개와 파일 N개를 보내 단일 요청을 N개의 외부 호출 묶음으로 증폭할 수 있다. 동일 attempt 반복 제출도 가능하다. Groq 비용/쿼터 고갈과 다수 virtual task·대형 `byte[]`에 의한 메모리 압박이 함께 발생한다.

**문서 충돌**

- `README.md:197`은 “사용자별 10회/시간”이 동작한다고 표시한다.
- `docs/CHANGELOG.md:9`도 rate limiting 완료를 선언한다.

---

### CORE-01. 모의고사 자기소개 문항은 정상 답변일수록 실패한다

> **✅ 완료 (2026-08-13)**: 제품 감사 `PC-01`과 동일 항목. 자기소개는 채점/태깅 없이 완료 처리하고 DB 저장도 안 함(문항 개수 통계 오염 방지). 상세는 `docs/product-contract-audit-2026-08-13.md`의 PC-01 참고.

**근거**

- `MockExamService.java:156-162`는 자기소개 `QuestionDto`의 `questionType`을 `null`로 만든다.
- live AI 경로는 `GroqService.java:193-196`에서 `question.getQuestionType().name()`을 호출한다.
- mock AI 경로도 이후 `FeedbackService.java:95-97`에서 같은 `.name()`을 호출한다.
- 예외는 `FeedbackService.java:151-167`에서 실패 DTO로 바뀐다.
- 실패 결과는 `PracticeAttemptApiController.java:130-137`의 완료 map에 들어가지 않는다.
- finalize는 `PracticeAttemptApiController.java:77-80`에서 15개 결과가 모두 있어야 하므로 끝나지 않는다.

**영향**

5단어 이상 자기소개를 정상적으로 말하면 live/mock 모두 첫 문항이 실패하고, 핵심 기능인 15문항 모의고사를 확정할 수 없다. 반대로 5단어 미만이면 `FeedbackService.java:84-87`의 “무응답” 조기 반환을 타서 저장될 수 있는데, 이때 `questionType=null`이 남아 `templates/analytics/history.html:40` → `ExamPlanService.java:195-207`에서 기록 화면 500으로 이어질 수 있다.

---

### DATA-01. finalize가 exactly-once를 보장하지 못한다

> **🟡 부분 완료 (2026-08-20 재리뷰)**: `IN_PROGRESS → FINALIZING → SUBMITTED` 전이와 Caffeine CAS로 단일 JVM의 일반 동시 요청을 차단했고, feedback/tag 저장도 하나의 Spring transaction으로 묶었다. 명확한 persistence 예외 뒤 상태 복구와 정상 완료 재요청도 단위 테스트로 고정됐다.
>
> 그러나 DB commit과 Caffeine `SUBMITTED` 확정이 별도 경계이고 `confirmSubmitted()`의 false를 무시한다. commit 성공/ACK 유실, cache eviction, commit 결과 불명확 예외에서 DB 완료 여부를 판정할 durable marker/unique가 없어 중복 저장 또는 완료 상태 유실이 가능하다. 따라서 기존의 “exactly-once 완료” 주장은 유지하지 않는다.
>
> 남은 최소 설계와 실제 transaction 테스트는 [`audit-followup-spec-2026-08-20.md#3-fu-01--db-기준-finalize-멱등원자성`](audit-followup-spec-2026-08-20.md#3-fu-01--db-기준-finalize-멱등원자성)을 따른다.

**근거**

- `PracticeAttemptApiController.java:73-90` 순서:
  1. attempt 유효성 확인
  2. 피드백/태그 DB 저장
  3. attempt를 `SUBMITTED`로 변경
- `:241`의 feedback `saveAll`과 `:255`의 tag `saveAll`은 컨트롤러 수준 트랜잭션으로 묶이지 않는다.
- `CaffeinePracticeAttemptStore.java:31-33`의 상태 변경은 compare-and-set이 아닌 find 후 put이다.
- `FeedbackResult.java:24-26`에는 `(attemptId, questionId)` 중복 방지 제약이 없다.
- 세션 결과도 `PracticeAttemptApiController.java:128-160`에서 일반 `HashMap`을 동기화 없이 read-modify-write한다.

**영향**

- tag 저장 실패 시 feedback만 커밋되고 attempt는 계속 유효하다. 재시도하면 feedback이 중복된다.
- 동시 finalize 두 요청이 모두 `IN_PROGRESS`를 통과해 같은 결과를 중복 저장할 수 있다.
- 병렬 부분 제출은 한 요청의 세션 map을 다른 요청이 덮어써 완료 결과를 잃을 수 있다.

이는 “트랜잭션을 붙였는가”보다 **DB 저장과 상태 전이를 하나의 멱등 작업으로 설계했는가**를 묻는 면접 포인트다.

---

### SCORE-01. 최신순 데이터의 가중치가 반대로 적용된다

> **✅ 완료 (2026-08-13)**: `ExamPlanService.weightedAvg()`/`weightedAvgList()`의 지수를 `n-1-i`에서 `i`로 수정해, index 0(최신)이 가장 큰 가중치를 갖도록 함. 테스트: `ExamPlanServiceWeightedAvgTest`(최신=5/과거=1, 최신=1/과거=5, MIN_FOR_WEIGHTED 경계).

**근거**

- `FeedbackResultRepository.java:11-12`는 `createdAt DESC`, 즉 최신순을 반환한다.
- `ExamPlanService.java:127` 주석도 최신순을 전제로 한다.
- 그러나 `ExamPlanService.java:139-143`은 `alpha^(n-1-i)`를 사용한다.
  - 첫 원소(최신): 가장 작은 가중치
  - 마지막 원소(가장 오래됨): 가중치 1
- 콤보/유형 평균인 `:158-162`도 동일하다.

**영향**

최근에 실력이 좋아지거나 나빠져도 오래된 기록이 더 강하게 반영된다. 학습 분석, 시험 계획, 코칭 리포트의 요소 점수까지 같은 함수를 사용하므로 파급 범위가 넓다.

---

### SCORE-02. “평가 제외”를 뜻하는 0점이 실제 감점으로 계산된다

> **✅ 완료 (2026-08-20, FU-02)**: 남아있던 5단어 미만 무응답 경로까지 닫음 — `noResponseDto()`가 questionType을 확인해 TYPE_5~7이면 `mainPointScore=null`을 쓰도록 수정(그 외 유형은 기존 MP=1 유지). 정상 길이/짧은 응답 모두 같은 `isRoleplayType()` 규칙을 공유해 이중화하지 않음. 테스트: `FeedbackServiceRoleplayMainPointTest`(TYPE_5~7 × 빈 문자열/1~4단어 파라미터라이즈드 + TYPE_1 대조군).

**근거**

- `GroqService.java:51-53`, `:130-146`은 TYPE_5~7의 `mainPointScore`를 “평가 제외, 0 고정”으로 지시한다.
- `FeedbackService.java:105-110`, `:251-264`는 0을 다른 점수와 그대로 평균 내 overall grade를 만든다.
- `ExamPlanService.java:128-144`, `:166-173`도 0을 유효 점수로 포함한다.

**영향**

예를 들어 MP=0, 나머지 네 항목=4라면 평가 제외 시 평균 4.0이지만 현재 평균은 3.2다. 사용자가 롤플레이를 많이 연습할수록 등급과 약점 분석이 구조적으로 낮아지고, “평가 제외” 항목이 가장 약한 항목으로 표시될 수도 있다.

---

### TEST-01. 전체 테스트가 안전하지도, 현재 계약을 검증하지도 않으며 배포 gate도 없다

**근거**

- `QuestionSetAdminIntegrationTest.java:70-75`, `:93-98`, `:112-115`
  - 삭제된 폼 POST 경로 `/admin/question-sets...`를 테스트한다.
  - 현재 계약은 `AdminQuestionSetApiController.java:19-47`의 JSON POST/PUT/DELETE다.
- `FullPipelineEndToEndTest.java:15-18`, `:78-87`, `:191-204`
  - 기본 `@Test`가 실제 Groq API와 환경변수/네트워크에 의존한다.
  - production `CoachingService`가 아니라 테스트 안에 집계 알고리즘을 다시 구현한다.
- `ManualSeedRunner.java:22-35`, `:87-105`
  - `@Disabled` 없이 기본 `@Test`로 등록되어 실제 Groq 호출과 로컬 DB insert/report 생성을 수행한다.
- `Dockerfile:5`는 `bootJar -x test`다.
- `.github/workflows/deploy.yml:11-20`은 checkout/build/test 없이 서버에서 pull 후 바로 배포한다.
- `PROJECT.md:127-128`은 “Docker가 없어서 실패할 뿐, 코드는 정상”이라고 적지만, Docker가 있어도 관리자 테스트의 URL/HTTP method assertion이 현재 코드와 맞지 않는다.

**영향**

`./gradlew test`가 반복 가능하고 무해한 검증 명령이 아니며, 배포 전에 자동으로 실행되는 회귀 방지선도 없다. 현재 발견된 관리자 권한, 모의고사 null, 채점 계산, finalize 문제를 막는 테스트가 없다.

## 4. 추가 P1/P2 보안·운영 항목

### SEC-05 [P1]. 공개 Grafana에 기본 관리자 비밀번호 fallback이 있다

> **✅ 완료 (2026-08-13)**: `docker-compose.prod.yml`의 `GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_PASSWORD:-admin}`을 `${GRAFANA_PASSWORD:?...}`로 변경 — 값이 없거나 비어있으면 `docker compose`가 즉시 실패한다(조용히 `admin`으로 넘어가지 않음). `--env-file /dev/null`로 값 없을 때 exit code 1과 에러 메시지 확인, 운영 서버 `.env`에 이미 값이 설정돼 있어 다음 배포에 영향 없음도 확인(값 자체는 출력하지 않고 존재 여부만 확인).

- `docker-compose.prod.yml:62-66`은 `GRAFANA_PASSWORD`가 없거나 비어 있으면 `admin`을 사용한다.
- `docker/nginx/nginx.conf.template:16-20`은 `/grafana/`를 외부 프록시한다.
- `deploy.sh:4-10`은 `.env` 파일 존재만 확인하고 필수 값의 non-empty 여부는 검사하지 않는다.

환경변수 누락 한 번으로 공개 관리 UI가 알려진 기본 비밀번호를 쓰게 된다.

### SEC-06 [P2]. OAuth 세션 앱에서 CSRF를 전역 비활성화했다

> **✅ 완료 (2026-08-13)**: `SecurityConfig`의 `.csrf(csrf -> csrf.disable())` 제거(기본 CSRF 설정 유지). Thymeleaf `th:action` 폼은 `thymeleaf-extras-springsecurity6`가 자동으로 토큰을 실어 별도 수정 불필요(전 템플릿에서 `<form>`이 `th:action`을 쓰는 것 확인, 예외는 GET 폼 1개·fetch 기반 폼 1개뿐). JS `fetch()`로 상태를 바꾸는 4개 템플릿(관리자 CRUD 2건, 마이페이지/온보딩 주제 토글, 답변 제출/finalize)엔 `<meta name="_csrf">`를 추가하고 각 fetch 헤더에 토큰을 실었다. 테스트에서 CSRF 필터가 꺼진 슬라이스(`addFilters=false`)는 `_csrf`가 null이라 템플릿이 깨지는 걸 발견해 `${_csrf != null ? ... : ...}`로 null-safe 처리. 회귀 테스트: `SecurityConfigAdminAccessTest`에 "ADMIN 권한이어도 CSRF 토큰 없으면 403" 케이스 추가(권한 체크(SEC-01)와 CSRF가 별개 관문임을 증명).

- `SecurityConfig.java:41-47`은 JSESSIONID 기반 OAuth 로그인/로그아웃을 쓰면서 CSRF를 전체 disable한다.
- 상태 변경 경로는 `MyPageController.java:56-118`, `ExamController.java:57-72`, `CoachingController.java:74-79`, `TodayController.java:89-97`, `OnboardingController.java:116-149`, admin CRUD다.
- 템플릿과 fetch 요청에도 CSRF token이 없다.

최신 브라우저의 기본 SameSite 정책이 일부 cross-site POST를 줄일 수는 있지만 CSRF token을 대체하지 않으며, same-site 하위 도메인/브라우저·프록시 설정에 따라 보호가 사라진다. 정적 보안 리뷰에서 바로 지적될 설정이다.

### SEC-07 [P2]. 운영 Actuator/Prometheus가 무인증 외부 경로다

> **✅ 완료 (2026-08-13)**: `docker/nginx/nginx.conf.template`에 `location /actuator/ { deny all; return 404; }` 추가 — 내부 Prometheus는 nginx를 거치지 않고 Docker network로 `opicnic_app:8080`에 직접 scrape하므로 영향 없음(`prometheus.prod.yml` 확인). `deploy.sh`가 이 템플릿을 매 배포마다 `nginx.conf`로 재생성하므로 다음 배포에 반영됨. `nginx -t`로 문법 유효성 확인(업스트림 호스트명 해석 실패만 나고 syntax 에러는 없음 — 고립 환경 테스트의 한계). Spring Security 쪽 `/actuator/**` permitAll 자체는 그대로 남겨둠(내부망에선 필요) — 외부 차단은 nginx 레이어에서 처리.

- `SecurityConfig.java:30`은 `/actuator/**`를 permitAll한다.
- `application.yml:81-85`는 `prometheus, health, info`를 노출한다.
- `docker/nginx/nginx.conf.template:7-14`는 모든 앱 경로를 외부로 전달한다.
- production Prometheus는 내부 Docker network에서 직접 scrape하므로 외부 공개가 필수는 아니다(`docker/prometheus/prometheus.prod.yml:4-8`).

JVM/HTTP/DB pool/process 메트릭과 서비스 상태가 인증 없이 수집 가능하다.

## 5. API/입력 검증 기본기

### API-01 [P2]. 잘못된 클라이언트 요청이 400이 아니라 500이 된다

> **✅ 완료 (2026-08-20, FU-04)**: handler 탐색 전(eager multipart parsing) 예외를 selector 없는 별도 `@RestControllerAdvice`(`MultipartExceptionHandler`)로 옮겨 처리 — `@RestController` selector가 있는 `ApiExceptionHandler`는 handler type이 아직 없는 이 시점엔 적용되지 않기 때문. 실제 `MultipartResolver`가 `resolveMultipart()` 단계에서 던지는 상황을 MockMvc로 재현해 413/400을 확인. 재리뷰 중 추가로 발견: handler가 이미 확정된 뒤(컨트롤러 내부) `PayloadTooLargeException`을 던지는 경로에선 두 advice가 모두 "적용 가능"해져, order 없이는 `ApiExceptionHandler`의 catch-all이 먼저 걸려 413 대신 500이 났다 — `MultipartExceptionHandler`에 `@Order(Ordered.HIGHEST_PRECEDENCE)`를 줘서 항상 먼저 검토되도록 고정. 테스트: `MultipartFrameworkExceptionIntegrationTest`(handler 탐색 전 413/400 2건 + 컨트롤러 내부 예외 413 1건 + 정상 경로 유지 1건), `ApiExceptionHandlerMultipartTest`.

- `ApiExceptionHandler.java:30-35`는 Bean Validation 예외만 400으로 처리한다.
- `:44-47`의 catch-all이 `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException` 같은 바인딩 오류도 500으로 만든다.
- `PracticeAttemptService.java:50`은 `questionIndexes=[null]`에서 NPE가 난다.

예상 결과:

- 깨진 JSON body → 500
- `/api/admin/question-sets/not-a-long` → 500
- `questionIndexes=[null]` → 500

클라이언트 오류가 서버 장애 지표와 retry 노이즈로 섞이고, API 소비자는 어떤 필드가 잘못됐는지 안정적으로 판단할 수 없다.

### API-02 [P2]. 핵심 도메인 검증이 브라우저에만 있다

> **✅ 완료 (2026-08-19)**: "배경설문" 절반은 이미 PC-11/후속 수정에서 `SurveyTopicPolicy.isValid()`(distinct 개수 기준이라 중복 제출 우회도 막음)로 서버 검증이 붙어있었음을 재확인. 남아있던 "시험 일정" 절반 — `ExamController.saveSchedule()`이 `dailyMinutes`/`studyDaysPerWeek`를 검증 없이 그대로 저장하던 것 — 을 화면이 실제로 보여주는 값 집합({30,60,90,120}/{3,5,7})으로 제한하고, 시험일이 과거면 거부하도록 수정. 실패 시 `?error=invalidSchedule` 배너. 테스트: `ExamControllerScheduleValidationTest`.

**배경설문**

- `templates/onboarding/onboarding-topics.html:184-198`은 12개 이상·그룹별 최소 개수를 검사한다.
- `OnboardingController.java:116-148`은 모든 값을 optional로 받고 서버 검증 없이 저장한다.
- `MyPageController.java:71-92`는 topics 파라미터가 없으면 기존 목록을 모두 지운다.
- `MyPageController.java:96-118`의 toggle도 최소 개수/`TopicCatalog` allowlist를 검사하지 않는다.
- `SurveyProfile.java:40-45`는 `List`라 반복 파라미터로 중복도 저장할 수 있다.

직접 POST하면 0개, 중복, 돌발/제외 주제를 포함한 profile이 만들어질 수 있다.

**시험 일정**

- UI는 `dailyMinutes={30,60,90,120}`, `studyDaysPerWeek={3,5,7}`만 보여준다(`templates/exam/prep.html:196-223`).
- `ExamController.java:57-71`은 임의 정수를 그대로 저장한다.
- `ExamPlanService.java:75-76`은 검증 없이 나누고 곱한다.

음수·과도한 값·overflow 결과가 학습 계획에 들어갈 수 있다.

### API-03 [P3]. 없는/남의 뷰 리소스를 404가 아니라 500으로 처리한다

- `HistoryController.java:41-47`, `CoachingController.java:59-71`, `TodayController.java:89-97`은 소유권 조건 조회를 해 IDOR은 막지만, 실패 시 인자 없는 `orElseThrow()`를 쓴다.
- `ApiExceptionHandler`는 `@RestController`에만 적용되어 이 `@Controller` 예외를 처리하지 않는다.

오래된 링크나 남의 ID는 404/403 대신 기본 500 화면으로 갈 가능성이 높다.

## 6. 기능·데이터·캐시 정확성

### ADMIN-01 [P1]. 관리자 목록 화면은 존재하지 않는 필드를 렌더링한다

> **✅ 완료 (2026-08-13)**: `QuestionSet`은 애초에 난이도 개념을 갖지 않으므로(난이도는 `SurveyProfile.preferredDifficulty`/런타임 `ComboPattern` 소관) null-safe 처리로 숨기지 않고 "난이도" 컬럼 자체를 제거. `findAll()`은 `@Where(clause = "deleted = false")`로 soft-deleted 세트를 이미 제외한다. 테스트: `AdminControllerQuestionSetsViewTest`.

- `templates/admin/question-sets.html:35`는 `${set.difficulty.name()}`을 참조한다.
- `QuestionSet.java:24-43`에는 `difficulty` 필드가 없다.

`GET /admin/question-sets`는 템플릿 평가 중 500이 날 가능성이 매우 높다. SEC-01 때문에 이 화면은 일반 로그인 사용자도 접근 가능하다.

### ADMIN-02 [P2]. 정상 관리자 CRUD만으로 출제 불가능한 세트를 만들 수 있다

> **🟡 부분 완료 (2026-08-20 재리뷰)**: topic이 정해진 뒤 `QuestionAssemblyService.assemble()`/`assembleSingle()`은 필요한 type을 한 set 안에 모두 가진 후보만 선택한다. `/practice/type`도 요청 type을 실제로 낼 수 있는 topic만 고른다. 하지만 랜덤, 돌발, 주제별/카테고리별 콤보, 집중연습 화면, 모의고사는 여전히 `findExistingTopics()`만으로 topic을 먼저 선택한다. 빈 set뿐인 topic이 선택되면 다른 정상 topic이 있어도 실패하며 모의고사는 500까지 가능하다. 후속 명세는 [`FU-05`](audit-followup-spec-2026-08-20.md#7-fu-05--모든-연습-진입점이-실제-조립-가능성을-사용)를 따른다.

- `AdminQuestionSetApiController.java:25-28`은 name/topic만 있는 빈 `QuestionSet`을 저장한다.
- 질문/콤보를 후속 추가하는 API는 없다.
- `QuestionSetRepository.java:22-23`은 빈 세트도 “존재하는 topic”으로 계산한다.
- `QuestionAssemblyService.java:42-45`, `:58-63`은 무작위로 그 세트를 고르면 필요한 `QuestionType`을 찾지 못해 실패한다.

관리자 UI의 정상 사용이 사용자 연습을 간헐적 또는 전면 실패시키는 구조다.

### CACHE-01 [P2]. 관리자 수정·삭제가 무기한 엔티티 캐시에 반영되지 않는다

> **✅ 완료 (2026-08-13)**: `QuestionAssemblyService.evict(topic)` 추가, `AdminQuestionSetApiController`의 create/update/delete가 각각 호출하도록 배선(update는 topic이 바뀔 수 있어 이전·이후 topic 둘 다 evict). TTL/size 기반 캐시로의 전면 재설계는 하지 않고 최소 수정만 적용. 테스트: `QuestionAssemblyServiceCacheTest`, `AdminQuestionSetApiControllerCacheTest`.

- `QuestionAssemblyService.java:27`, `:31-34`, `:49-52`는 `QuestionSet` JPA 엔티티 목록을 TTL/size/무효화 없이 `ConcurrentHashMap`에 저장한다.
- `AdminQuestionSetApiController.java:31-47`은 repository만 변경하고 캐시를 비우지 않는다.

한 번 조회된 topic은 수정 전 내용, 이전 topic 분류, 논리 삭제된 세트를 프로세스 재시작 전까지 계속 출제할 수 있다.

### DOMAIN-01 [P2]. 단일 “돌발 연습”이 돌발 전용 풀을 사용하지 않는다

> **✅ 완료 (2026-08-13)**: 제품 감사 `PC-02`와 동일 항목. `HomeController.surprisePractice()`가 `surpriseTopics()` 전용 풀만 사용하도록 수정. 상세는 `docs/product-contract-audit-2026-08-13.md`의 PC-02 참고.

- `DOMAIN.md`는 돌발 문제를 `TopicCatalog.surpriseTopics()`의 전용 23개 풀에서 뽑는다고 명시한다.
- `HomeController.java:117-130`의 `/practice/surprise`는 `topicCatalog.practiceTopics()`를 사용한다.
- `TopicCatalog.java:90-92`의 `practiceTopics()`는 일반 지원 주제와 돌발 주제를 합친 목록이다.

따라서 “돌발 연습”에서 사용자가 이미 선택하는 일반 배경설문 주제가 나올 수 있다. `MockExamService`는 전용 풀을 올바르게 사용하지만 단일 연습 경로만 다르다.

### AI-01 [P2]. 외부 LLM 응답을 domain validation 없이 정상 데이터로 저장한다

> **✅ 완료 (2026-08-20, FU-06)**: 남아있던 답변 단위 중복 문제까지 닫음 — `addTags()`가 `LinkedHashSet`으로 카테고리당 distinct tag만 남기도록 수정(중복/blank/unknown은 5개 상한을 소비하지 않음). `CoachingService`의 요소별/유형별 집계도 row 개수가 아니라 `Set<feedbackResultId>` 크기로 바꿔, DB에 이미 남아있는 중복 row로도 답변 하나가 반복 패턴 기준(MIN_PATTERN_COUNT=3)을 혼자 충족하지 못하게 방어. 테스트: `FeedbackServiceScoreValidationTest`(중복 6개→distinct 1개, allowlist 밖/중복 혼재→최초 등장 순서만 유지), `CoachingServiceDistinctOccurrenceTest`(같은 답변 중복 row는 occurrence 1, 서로 다른 답변 3개는 occurrence 3).

- `FeedbackService.java:278-295`는 태그 JSON 파싱 실패를 빈 목록으로 바꾼다. 실제 “태그 없음”과 장애를 구분하지 못한다.
- `:298-300`은 category/tag allowlist와 길이를 확인하지 않는다.
- `:308-312`는 점수가 정수인지만 보고 1~5 범위 clamp/reject를 하지 않는다.
- 실제 응답 형식은 JSON object mode이지 backlog가 말하는 minimum/maximum JSON Schema 강제가 아니다(`GroqService.java:199-203`).

모델이 malformed JSON, `score=99`, 과도하게 긴 tag를 반환하면 분석 수치 오염, 태그 유실, DB 저장 실패가 발생한다. 외부 API 응답도 신뢰 경계 밖 입력이라는 기본기가 빠진 상태다.

### DATA-02 [P2]. OAuth 회원 데이터 무결성을 DB가 보장하지 않는다

> **✅ 완료 (2026-08-19)**: (1) 신규 가입 시 `member.setNotificationSetting(...)`만 하고 FK 소유 쪽인 `notificationSetting.setMember(...)`을 안 해서 `member_id=null` orphan row가 매번 생기던 문제 → 양쪽 다 설정하도록 수정. (2) `Member`에 `(provider, providerId)` DB unique 제약 추가, `CustomOAuth2UserService.loadUser()`에서 `@Transactional`을 제거(전체를 하나의 트랜잭션으로 묶으면 유니크 제약 위반을 잡아도 Spring이 이미 rollback-only로 표시해 커밋 시점에 `UnexpectedRollbackException`이 남 — 각 리포지토리 호출이 자체 트랜잭션을 쓰도록 분리)하고, 동시 콜백이 존재 확인을 둘 다 통과해 저장이 유니크 제약을 위반하면 이미 다른 요청이 만든 회원으로 재조회해 수렴하도록 함. 테스트: `CustomOAuth2UserServiceMemberCreationTest`.

**알림 설정 관계**

- `Member.java:30-31`은 `mappedBy` inverse side다.
- FK owner는 `NotificationSetting.java:21-24`의 `notificationSetting.member`다.
- 신규 가입은 `CustomOAuth2UserService.java:64-65`에서 `member.notificationSetting`만 설정하고 owner 쪽 member를 설정하지 않는다.
- 이후 `MyPageController.java:38-43`은 회원에 연결된 설정을 못 찾아 새 행을 만든다.

신규 회원마다 `member_id=null` orphan 설정이 생길 수 있다.

**외부 사용자 식별자**

- `Member.java:27-28`의 `(provider, providerId)`에는 DB unique 제약이 없다.
- `CustomOAuth2UserService.java:55-65`는 조회 후 insert라 동시 OAuth callback에서 경쟁 가능하다.
- `MemberRepository.java:11`은 결과가 한 건이라는 전제의 `Optional`을 반환한다.

중복 회원이 생기면 이후 로그인이 다중 결과 예외로 깨질 수 있다.

### PERF-01 [P2]. 일반 화면이 모든 대용량 피드백 엔티티를 무제한 로드한다

> **✅ 완료 (2026-08-19)**: `HistoryController`는 이미 `Pageable`로 20개씩 페이징하고 있어 대상이 아니었음(재확인). 실제 무제한 전체 로드는 `AnalyticsController`/`ExamController`/`TodayController`/`HomeController` 4곳 — 확인해보니 이 넷은 모두 점수·등급·유형·comboCategory·createdAt 요약 필드만 쓰고 TEXT 컬럼 15개는 전혀 렌더링하지 않았다. `FeedbackResultRepository.findSummaryByMemberId()`(JPQL constructor expression)를 추가해 이 요약 필드만 DB에서 SELECT하도록 하고 4곳 모두 교체. 실제 MySQL(Testcontainers)에 대해 필드 매핑이 맞는지, TEXT 컬럼이 정말 안 실려오는지(null) 검증. 테스트: `FeedbackResultRepositorySummaryTest`.

- `FeedbackResult.java:37-99`는 STT, 진단, quote/fix, 모범답변 등 다수 TEXT 컬럼을 가진다.
- `FeedbackResultRepository.java:11`은 회원의 전체 엔티티를 최신순으로 반환한다.
- `AnalyticsController.java:48`, `ExamController.java:36`, `TodayController.java:55`, `HomeController.java:74`가 이 전체 조회를 사용한다.

사용 이력이 쌓일수록 홈·분석·오늘 화면에서 불필요한 TEXT까지 전송하고 힙에 올린다. 고확신 classic N+1은 발견하지 못했으며, 여기서는 N+1보다 **무제한 over-fetch**가 실제 문제다.

## 7. 테스트·배포·재현성

### TEST-02 [P2]. 공식 k6 재현 절차가 clean clone에서 동작하지 않는다

> **🟡 부분 완료 (2026-08-20 재리뷰)**: `POST /api/practice-attempts/start`(dev 프로파일 전용, 로그인 세션 없이 attempt 생성)를 `DevPracticeController`로 복원 — 과거 커밋(34466d5)에서 sequential feedback 경로 정리하며 함께 삭제됐던 걸 확인. `load-test.js`의 `/answers` 경로를 `/{attemptId}/answers`로 실제 라우트에 맞게 수정. `test_audio.webm`(1MB)을 `.gitignore` 예외 처리해 커밋. `REPRODUCTION_GUIDE.md`의 `AI_GEMINI_ENABLED` → `LLM_ENABLED` 정정 + `SPRING_PROFILES_ACTIVE=dev` 필요함을 명시.
>
> 로컬 MySQL 띄우고 실제로 `SPRING_PROFILES_ACTIVE=dev LLM_ENABLED=false STT_ENABLED=false ./gradlew bootRun` + `k6 run scripts/load-test.js`로 end-to-end 실행 검증. 그 과정에서 두 가지를 추가로 발견/수정:
> 1. SEC-06(CSRF 재활성화)의 부작용으로 k6의 무세션 POST가 로그인 페이지로 302 리다이렉트됨 → `DevPracticeController`에 `GET /api/practice-attempts/csrf` 토큰 발급 엔드포인트 추가, 스크립트가 먼저 토큰을 받아 헤더에 실어 보내도록 수정(CSRF 보호 자체는 그대로 유지 — 우회하지 않음).
> 2. **실제 프로덕션 버그 발견**: `PracticeAttemptService.questionCache`가 `Question` JPA 엔티티를 앱 수명 내내 캐싱하는데, `Question.questionSet`이 `FetchType.LAZY`라 어떤 요청이 엔티티를 로드해 캐시에 넣은 뒤, **다른 요청**이 캐시 히트로 그 엔티티를 재사용하며 `questionSet.getTopic()`을 부르면 이미 닫힌 영속성 컨텍스트의 프록시를 건드려 `LazyInitializationException`(500)이 남. 실제 부하테스트로 재현 확인(45% 실패율 중 다수가 이 예외). `questionCache`가 엔티티 대신 `QuestionDto`(topic이 이미 문자열로 풀린 값)를 캐싱하도록 수정해 해결 — 캐시 히트 경로가 더 이상 엔티티/프록시를 건드리지 않음. 재현 재실행으로 500이 0건이 됨을 확인(남은 실패는 전부 COST-01 rate limiter의 정상 429).
>
> 이 lazy-loading 버그는 실사용자가 문제를 제출할 때 언제든 터질 수 있는 경로였고, 재현 가능한 부하테스트가 이번까지 없었던 게 발견을 막고 있었다는 점에서 TEST-02가 왜 필요했는지 그 자체로 증명한 셈. 테스트: `PracticeAttemptServiceQuestionCacheTest`(캐시 히트 시 LAZY 연관관계를 다시 건드리지 않음을 mock으로 검증 — fix 되돌리면 실패하는 것 확인함).
>
> **✅ 완료 (2026-08-20, FU-03)**: dev anonymous VU 전체가 `dev-loadtest`라는 또 다른 유한 버킷(시간당 15)을 공유해 20~100 VU 실행이 곧 429가 되던 문제 → `RateLimiterService.tryConsume(cost, attemptMemberId)`로 계약을 바꿔, dev 프로파일이면서 `attemptMemberId==null`(=`DevPracticeController`가 로그인 세션 없이 만든 k6 attempt)인 조합만 소비 자체를 건너뛰도록 함. dev의 로그인 회원 attempt와 production 익명은 기존 시간당 15문항 한도를 그대로 유지. 프로파일 판정도 활성 프로파일 배열 대신 `Environment.acceptsProfiles(Profiles.of("dev"))`로 바꿔 `spring.profiles.default=dev`(활성 프로파일 미지정) 케이스도 인식하도록 함. 테스트: `RateLimiterServiceDevProfileTest`(active dev/default dev + null memberId는 무제한, active dev + 실제 memberId·production + null memberId는 기존 15회 한도 유지). k6 실제 재실행(`error_rate < 0.05` 통과)까지는 이번 세션에서 직접 수행하지 않음 — 재현 절차·수치 갱신은 별도 확인 필요.

- `scripts/load-test.js:14`는 `test_audio.webm`을 연다.
- `scripts/*.webm`은 `.gitignore:64`에 걸려 추적되지 않는다. clean clone에는 `scripts/test_audio.wav`만 있다.
- `scripts/load-test.js:65`는 없는 `POST /api/practice-attempts/start`를 호출한다.
- `:96`은 없는 `POST /api/practice-attempts/answers`를 호출한다. 현재 경로에는 `{attemptId}`가 필요하다.
- `docs/performance/REPRODUCTION_GUIDE.md:14`의 `AI_GEMINI_ENABLED=false`는 현재 설정 키 `LLM_ENABLED`와 다르다.
- 같은 가이드 `:20-24`가 이 스크립트를 공식 재현 방법으로 안내한다.

현재 README의 성능 수치가 거짓이라는 뜻은 아니다. 다만 면접관이 문서대로 재현 스크립트를 돌리면 파일 또는 라우트 단계에서 실패하므로, **현재 커밋에서 수치가 재현 가능하다는 증거 체인**은 끊겨 있다.

### DOC-01 [P2]. 완료 문서와 현재 코드가 정면으로 충돌한다

> **✅ 완료 (2026-08-19)**: 세 claim을 재검증. (1) `README.md`의 "10회/시간" — COST-01에서 이미 실제 코드가 바뀌었으므로(시간당 15문항, 검증 통과 후 소비) README 문구를 현재 동작에 맞게 수정. (2) `CHANGELOG.md`의 REST 정비 완료 claim — CHANGELOG는 "과거 항목은 안 고친다"는 자체 append-only 원칙이 있어 그 시점 기록은 그대로 두고, 이번 세션에서 실제로 고친 API-01/API-02를 새 항목으로 추가해 간극을 메움(과거 claim 자체를 지금 다시 참이 되게 만든 셈). (3) `PROJECT.md`의 "Docker만 없어서 테스트 실패, 코드는 정상" — 실제로 확인해보니 거짓이었음: `QuestionSetAdminIntegrationTest`는 Docker/Testcontainers가 정상 작동해도 실패했다. 원인은 SEC-01(ADMIN 권한 강제)·SEC-06(CSRF 재활성화) 이후에도 이 테스트가 인증/CSRF 없이 호출했고, 심지어 호출하는 `/admin/question-sets` form 경로 자체가 REST API(`/api/admin/question-sets`)로 이전되며 사라진 상태였음(테스트 계약이 완전히 구식). 현재 REST 경로 + ADMIN 인증 + CSRF로 재작성해 통과 확인, `PROJECT.md` 문구도 정정.

- `README.md:197`, `docs/CHANGELOG.md:9`: 10회/시간 limiter가 동작한다고 함 ↔ 실제 matcher는 죽은 경로.
- `docs/CHANGELOG.md:20`: REST 경로, 공통 에러, 검증 정비 완료 ↔ malformed JSON 500, multipart/설문 검증 누락.
- `PROJECT.md:127-128`: Docker만 없어서 테스트가 실패하며 코드는 정상 ↔ 관리자 테스트 계약 자체가 구형.

면접관은 완성도보다 “문서의 주장과 코드를 대조했는가”를 볼 수 있으므로 코드 결함과 별개로 리스크가 크다.

### OPS-01 [P3]. 날짜 계산 timezone이 배포 환경 기본값에 의존한다

- `HomeController.java:83`, `TodayController.java:64,130`, `ExamPlanService.java:70`은 `LocalDate.now()`를 직접 사용한다.
- `Dockerfile`, `docker-compose.prod.yml`, `application-prod.yml`에는 `TZ`, `-Duser.timezone`, 명시적 `ZoneId/Clock` 설정이 없다.
- DB URL의 `serverTimezone=Asia/Seoul`은 JVM의 `LocalDate.now()` timezone을 바꾸지 않는다.

컨테이너/JVM 기본 timezone이 UTC라면 KST 00:00~09:00에 오늘 진행률, 회피 일수, D-day가 하루 어긋날 수 있다. 최소한 배포 환경에 따라 결과가 달라지는 상태다.

### DB-01 [P3]. 운영 스키마 변경이 버전 관리되지 않는다

- `application-prod.yml:13-16`도 `spring.jpa.hibernate.ddl-auto=update`다.
- Flyway/Liquibase 또는 별도 migration 파일이 없다.
- `DataInitializer`가 startup에 sentinel 조회 후 production data를 삽입한다(`DataInitializer.java:28-45`).

즉 배포 시 애플리케이션 시작 자체가 스키마와 기준 데이터 변경 작업이다. 작은 프로젝트에서 선택할 수는 있지만, rollback·변경 이력·실패 복구를 어떻게 보장하는지 면접 질문이 바로 생긴다.

## 8. OOP/설계 냄새 중 실제로 남길 것

### DESIGN-01 [P3]. 출제 source of truth가 둘처럼 보이는 죽은 도메인 경로가 있다

- 실제 출제는 runtime `ComboPattern`을 사용한다(`DOMAIN.md`, `MockExamService`, `QuestionAssemblyService`).
- 동시에 `QuestionSet.java:39-41`은 영속 `Combo` 컬렉션을 유지한다.
- `DataInitializer.java:48-54`는 모든 세트에 영속 Combo를 계속 생성한다.
- `ComboQuestionStrategy` 구현체들은 실제 consumer가 없고, `FixedComboQuestionStrategy.java:19-37`은 임의 ID를 넣은 샘플 엔티티를 Spring service bean으로 유지한다.

단순히 클래스가 많다는 문제가 아니다. 데이터베이스 `Combo`와 runtime `ComboPattern` 중 무엇이 진짜 규칙인지 코드만 보면 두 source of truth처럼 보여 설명 비용과 잘못된 수정 가능성이 커진다. `PROJECT.md`가 이를 별도로 경고해야 할 정도면 설계 부채가 이미 외부에 노출된 상태다.

## 9. 확인 결과와 확인하지 않은 것

### 실행한 안전한 확인

- `./gradlew compileJava --no-daemon` → 성공
- `./gradlew test --tests com.opicnic.opicnic.service.GroqServiceTest --no-daemon` → 5개 단위 테스트 성공
- `./gradlew dependencyInsight --dependency tomcat-embed-core --configuration runtimeClasspath` → 10.1.39 확인
- route/권한/secret/cache/test annotation 정적 검색
- Git 추적 여부와 resource 복사 결과 확인

### 의도적으로 실행하지 않음

- 일반 USER로 관리자 CRUD 악용
- 운영/로컬 DB에 seed 또는 실패 주입
- 실제 Groq 반복 호출
- 전체 `./gradlew test`
  - 현재 suite에는 실제 Groq 호출과 DB insert를 하는 기본 `@Test`가 포함되어 있어 발견 전용 감사에서 실행하기 부적절하다.
- Docker build 및 live 서비스 부하/보안 테스트

### 이번 감사에서 고확신 문제를 찾지 못한 영역

- History/Coaching/Today/PracticeAttempt의 직접 IDOR: member 조건 조회/owner 검사를 하고 있다.
- SQL injection: 확인한 쿼리는 Spring Data parameter binding을 사용한다.
- SSRF: 외부 STT URL은 서버 상수이고 사용자가 URL을 주입하는 경로를 찾지 못했다.
- 저장형 XSS: 주요 STT/LLM 출력은 `th:text` 또는 `textContent`로 렌더링한다.
- 핵심 조회의 classic N+1: fetch join과 `@BatchSize`가 있어 확정하지 않았다. 대신 PERF-01의 over-fetch는 확정적이다.

### 감사 후 판정: 성능 개선 원인 귀속은 이슈에서 제외

README의 `제출 p95 20.5s → 3.73s` 개선을 인메모리 캐시 적용 성과로 설명한 것은 유효한 원인 귀속으로 판정한다.

- 캐시 적용은 반복적인 `Question` 조회를 제거할 수 있는 선행 조건이었다.
- 캐시 도입 직후에는 기존 `@Transactional`이 캐시 히트 경로에서도 DB 커넥션을 선점해 캐시 효과를 가리고 있었다.
- 해당 어노테이션 제거는 캐시와 무관한 별도 최적화라기보다, 캐시 히트가 실제로 “DB 조회와 커넥션 획득이 없는 경로”가 되도록 완성한 후속 조치다.
- 캐시가 없었다면 어노테이션만 제거해도 매 제출의 문제 복원 DB 조회가 남으므로 동일한 결과를 만들 수 없다.

따라서 성능 개선을 “캐시 적용”으로 요약한 표현 자체는 과장 또는 잘못된 귀속으로 보지 않는다. 다만 오해를 피하도록 README에는 **캐시 도입 → 남아 있던 트랜잭션 경계 발견·제거 → 캐시 히트 경로 완성**이라는 원인 사슬을 명시했다. 상세 실험 과정과 중간 수치는 `docs/local/2026-06-11-question-cache-load-test.md`에 보존되어 있다.

## 10. 후속 구현 에이전트 인계

이 절은 위 발견을 실제 수정 작업으로 전환할 때 사용한다. 이 문서의 앞부분은 “무엇이 왜 문제인가”의 근거이고, 이 절은 “어디까지 고쳐야 완료인가”의 기준이다.

### 10.1 공통 작업 규칙

1. 항목 하나를 맡으면 먼저 해당 ID의 근거 파일과 현재 테스트를 다시 읽는다.
2. 현재 오동작을 재현하는 자동 테스트를 먼저 추가한다. 운영 DB·실제 Groq·외부 계정에 의존하는 테스트는 기본 suite에 넣지 않는다.
3. 아래 `READY`는 목표 계약이 명확하다는 뜻이다. 구현 방법까지 하나로 고정됐다는 뜻은 아니다.
4. `MIXED`는 안전한 최소 수정과 장기 수정이 나뉜다. 선행 결정 없이 장기 설계를 임의로 확정하지 않는다.
5. `DECISION`은 문서만 보고 구현을 시작하지 않는다. 적힌 질문에 대한 사용자 결정을 받은 뒤 acceptance criteria를 확정한다.
6. 한 항목을 수정하면서 다른 ID를 우연히 가리지 않는다. 예를 들어 rate limit만 붙여 `COST-01`의 duplicate index 증폭까지 해결됐다고 선언하면 안 된다.
7. 완료 시 코드·테스트 외에 이 감사 문서의 상태, `docs/backlog.md`, `docs/CHANGELOG.md`, 필요하면 `PROJECT.md`를 갱신한다.

### 10.2 작업 준비도 요약

| ID | 준비도 | 주요 선행 조건 |
|---|---|---|
| SEC-01 | READY | 없음. authority 문자열이 `ADMIN`인 현재 구현을 유지할지 테스트로 고정 |
| SEC-02 | MIXED | 저장소 제거는 즉시 가능, 실제 credential 회전은 외부 상태 확인·권한 필요 |
| SEC-03 | READY | 없음 |
| SEC-04 | READY | 수정 시점의 안전한 Spring Boot/Tomcat 버전을 공식 advisory로 다시 확인 |
| COST-01 | MIXED | 경로/입력 방어는 즉시 가능, 사용자·IP·attempt·전역 비용 예산은 정책 결정 필요 |
| CORE-01 | MIXED | null 장애 제거는 즉시 가능, 자기소개를 채점/분석에 포함할지는 제품 결정 필요 |
| DATA-01 | MIXED | exactly-once 불변식은 명확, DB-backed finalization 모델 선택 필요 |
| SCORE-01 | READY | 없음 |
| SCORE-02 | READY | “평가 제외는 평균 분모에서 제외”를 공통 규칙으로 적용 |
| TEST-01 | READY | CI 제공자/배포 workflow는 현재 GitHub Actions 기준 |
| SEC-05 | READY | 실제 운영 비밀번호 값은 문서나 로그에 출력하지 않음 |
| SEC-06 | READY | 세션 기반 OAuth/폼/fetch 요청 전체에 CSRF token 적용 |
| SEC-07 | MIXED | 외부 공개할 actuator endpoint가 있는지 결정; 기본값은 내부 전용 |
| API-01 | READY | 공통 error shape 유지 |
| API-02 | MIXED | 서버 검증은 즉시 가능, 주제 12개 카운트의 세부 정책은 제품 문서 PD-01/설문 규칙 확인 |
| API-03 | READY | view 404/403 정책 확정 |
| ADMIN-01 | READY | 없음 |
| ADMIN-02 | DECISION | 관리자 편집 단위를 세트 전체 aggregate로 할지 질문 별 API로 할지 결정 |
| CACHE-01 | READY | admin mutation과 cache invalidation을 같은 service 경계에 둠 |
| DOMAIN-01 | READY | `DOMAIN.md`의 돌발 전용 풀 사용 |
| AI-01 | READY | 점수·enum·길이·tag allowlist의 서버 schema 확정 |
| DATA-02 | READY | 운영 데이터 중복 정리 후 DB unique 추가 순서 주의 |
| PERF-01 | MIXED | 화면별 pagination/recent-window 범위 결정 |
| TEST-02 | READY | 실재 라우트와 추적 가능한 작은 audio fixture 사용 |
| DOC-01 | READY | 원인 코드를 고친 뒤 문서를 현재 계약에 맞춤 |
| OPS-01 | READY | 제품 기준 timezone은 `Asia/Seoul`; 테스트에는 `Clock` 사용 |
| DB-01 | DECISION | Flyway/Liquibase 선택, 기존 운영 DB baseline 결정 |
| DESIGN-01 | DECISION | 영속 `Combo`를 제거할지 실제 source of truth로 승격할지 결정 |

### 10.3 보안·비밀·운영 항목 완료 조건

#### SEC-01 — 관리자 권한 경계

**수정 목표**

- `/admin/**`와 `/api/admin/**`를 실제 `ADMIN` authority만 접근하게 한다.
- 신규 OAuth 회원의 `USER` authority와 관리자 authority 생성 방식을 한 규칙으로 맞춘다.

**필수 테스트**

- 비인증 요청: 로그인 redirect 또는 API 401
- 일반 `USER`: UI/API 모두 403
- `ADMIN`: 목록과 POST/PUT/DELETE가 허용
- `/api/**`의 다른 공개/보호 정책이 matcher 순서 변경으로 회귀하지 않음

**완료가 아닌 것**

- 관리자 링크만 숨기는 것
- 컨트롤러 내부 nickname/email 비교
- API만 막고 `/admin/**` 뷰는 계속 일반 회원에게 여는 것

#### SEC-02 — 저장소 자격증명

**즉시 가능한 코드 작업**

- `application.properties.old`를 추적 대상과 빌드 리소스에서 제거한다.
- secret scanner를 CI에 추가하고 known-secret fixture는 명시적으로 격리한다.
- 문서·로그·테스트 출력에 실제 값을 재기록하지 않는다.

**외부 조치**

- 값이 실제 사용됐는지 소유자가 확인한다.
- 사용 가능성이 있으면 DB/Kakao credential을 회전한다.
- Git history 정리가 필요한지는 저장소 공개 범위와 유출 대응 정책에 따라 결정한다. 현재 파일만 삭제했다고 회전까지 완료된 것으로 표시하지 않는다.

**완료 조건**

- clean clone과 빌드 jar/resource에 파일·값이 없다.
- secret scan이 통과한다.
- 필요한 credential 회전 상태가 값 자체 없이 기록된다.

#### SEC-03 — Docker build context

**수정 목표**

- `.dockerignore`에 최소 `.env`, `.git`, build 산출물, IDE/로컬 자료, 불필요한 performance dump를 제외한다.
- Dockerfile이 빌드에 필요한 파일만 복사하도록 가능하면 범위를 좁힌다.

**필수 검증**

- dummy `.env`를 둔 build context 또는 build-stage 검사에서 `/app/.env`가 존재하지 않는다.
- `.git`도 build stage에 없다.
- clean Docker build는 성공한다.
- 실제 secret 내용은 검증 로그에 출력하지 않는다.

#### SEC-04 — 취약 Tomcat

**수정 목표**

- Spring Boot dependency management를 통해 취약 범위를 벗어난 Tomcat을 사용한다. 개별 Tomcat jar만 임의 override하는 것보다 호환되는 Boot patch upgrade를 우선 검토한다.

**필수 검증**

- `dependencyInsight`로 실제 runtime Tomcat 버전을 기록한다.
- 공식 Apache advisory 기준 해당 CVE 영향 범위 밖인지 확인한다.
- multipart 제출과 기본 애플리케이션 테스트가 통과한다.
- dependency/security scan이 같은 finding을 다시 내지 않는다.

#### SEC-05 — Grafana 기본 비밀번호

**수정 목표**

- `admin` fallback을 제거하고 강한 `GRAFANA_PASSWORD`가 없으면 배포를 fail-fast한다.
- Grafana의 외부 공개가 필요한지 검토하고, 필요하면 별도 인증/접근 제어를 둔다.

**필수 검증**

- 환경변수 누락과 빈 문자열 모두 compose/deploy 단계에서 실패한다.
- 완성된 compose config에 알려진 기본 비밀번호가 없다.
- 비밀번호 값은 명령 출력이나 문서에 나타나지 않는다.

#### SEC-06 — CSRF

**수정 목표**

- 세션 기반 browser 요청에 CSRF 보호를 활성화한다.
- Thymeleaf form, JavaScript `fetch`, multipart 답변 제출, admin JSON mutation에 token을 전달한다.
- 정말 stateless한 별도 API만 근거가 있을 때 제한적으로 예외 처리한다.

**필수 테스트**

- token 없는 상태 변경 요청은 403이다.
- 정상 화면에서 생성한 token이 있는 폼/fetch는 성공한다.
- OAuth login/logout과 multipart upload가 회귀하지 않는다.
- GET에는 상태 변경이 없다.

#### SEC-07 — Actuator/Prometheus 노출

**최소 안전 목표**

- production 외부 nginx 경로에서 Prometheus와 상세 actuator 정보에 접근할 수 없게 한다.
- 내부 Prometheus scrape는 Docker network에서 계속 동작하게 한다.

**선행 결정**

- 외부 health endpoint가 로드밸런서/모니터링에 필요한지 결정한다.
- 필요하다면 최소 health 정보만 별도 경로/권한으로 공개한다.

**필수 검증**

- 외부 비인증 `/actuator/prometheus`는 거부된다.
- 내부 Prometheus scrape는 성공한다.
- health 공개 범위가 설정과 문서에 일치한다.

### 10.4 비용·입력·API 항목 완료 조건

#### COST-01 — 외부 AI 비용과 작업 증폭

**즉시 수정할 방어선**

- limiter를 실제 `/api/practice-attempts/{id}/answers`, retry, 코칭 생성 경로에 적용한다.
- `questionIndexes`의 null·duplicate를 거부한다.
- 목록 길이는 해당 attempt의 남은 고유 문항 수를 넘지 못하게 한다.
- 파일 개수, 개별/전체 크기, 허용 MIME/컨테이너를 검증한다.
- 동일 question이 성공 처리된 뒤 다시 비용 호출되지 않게 한다.

**사람이 정할 예산**

- IP, member, attempt, question, 전역 provider quota 각각의 제한
- 답변 1문항이 STT+채점+태깅과 내부 재시도로 얼마까지 증폭 가능한지
- 429/timeout 시 retry budget과 사용자 안내

**필수 테스트**

- `[0,0]`, `[null]`, 과도한 index 목록, 파일 불일치가 외부 호출 전에 400
- 같은 attempt/question 반복 제출이 정한 정책대로 거부 또는 캐시된 결과 반환
- 실제 비용 경로에서 제한 초과 시 429
- 서로 다른 정상 문항의 retry는 허용
- 외부 client는 mock/stub으로 호출 횟수를 assertion

#### API-01 — 클라이언트 오류 상태 코드

**수정 목표**

- `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, multipart parse/size 예외, 명시적 validation 오류를 4xx로 분류한다.
- 예상하지 못한 서버 예외만 500으로 남긴다.

**필수 테스트**

- 깨진 JSON, 잘못된 enum, `id=abc`, `[null]`, 누락 multipart parameter 각각의 status와 공통 `ErrorResponse`
- 내부 예외 stack/message가 client에 노출되지 않음
- 정상 요청의 status 계약 유지

#### API-02 — 서버 도메인 검증

**수정 목표**

- 온보딩, 마이페이지 form, topic toggle이 하나의 profile validation 규칙을 사용한다.
- 시험 일정의 날짜·목표·하루 시간·주 일수를 서버에서 검증한다.
- browser JavaScript는 동일 규칙을 미리 안내할 뿐 source of truth가 아니다.

**필수 테스트**

- 주제 0/11/12개, 그룹별 최소 미달, 중복, 허용되지 않은 돌발/제외 주제
- 누락 파라미터가 기존 profile을 삭제하지 않음
- 시험 시간/일수의 음수·0·허용 범위 밖 값과 과거 날짜
- 잘못된 요청은 DB 상태를 변경하지 않음

세부 제품 규칙은 [`product-contract-audit-2026-08-13.md`](product-contract-audit-2026-08-13.md)의 PC-05, PC-11, PD-01을 함께 본다.

#### API-03 — view resource 오류 의미

**수정 목표**

- 존재하지 않는 기록/리포트는 404, 존재하지만 정책상 감춰야 하는 남의 리소스도 일관된 404 또는 명시적 403을 반환한다.
- `@RestControllerAdvice`와 별개로 view controller의 예외 매핑을 둔다.

**필수 테스트**

- 없는 ID, 다른 회원 ID, 정상 owner ID에 대한 history/coaching/today 동작
- 404/403 error page가 500으로 렌더링되지 않음

#### AI-01 — LLM 출력 신뢰 경계

**수정 목표**

- score는 허용 범위로 reject 또는 정책적으로 clamp한다. 어떤 방식을 썼는지 테스트로 고정한다.
- grade/type/category/tag는 allowlist enum으로 검증한다.
- 텍스트/quote/fix/model answer/tag 개수와 길이에 상한을 둔다.
- malformed JSON과 누락 필드를 부분 성공으로 저장할지 전체 실패시킬지 정한다.

**필수 테스트**

- `score=-1/0/6/99`, 문자열 score, unknown grade/tag, 과도한 길이, malformed JSON
- 잘못된 출력이 분석 통계와 DB에 정상 데이터처럼 들어가지 않음
- 사용자에게 재시도 가능한 실패와 영구 실패를 구분해 전달

### 10.5 핵심 기능·데이터·채점 항목 완료 조건

#### CORE-01 — 모의고사 자기소개

제품 계약과 상세 테스트는 [`product-contract-audit-2026-08-13.md`](product-contract-audit-2026-08-13.md)의 PC-01을 source of truth로 사용한다.

최소 기술 완료 조건은 다음과 같다.

- 자기소개 정상 답변이 null dereference 없이 처리된다.
- 15개 모든 문항을 제출하고 finalize할 수 있다.
- 자기소개를 채점/태깅/통계에 포함 또는 제외하는 규칙이 한 곳에 명시된다.
- 기록 화면도 null type 때문에 실패하지 않는다.

#### DATA-01 — 멱등·원자적 finalize

**반드시 보장할 불변식**

1. attempt의 owner, mode, 문제 순서와 메타데이터는 생성 후 변하지 않는다.
2. question ordinal은 `0..N-1`의 고유 값이다.
3. 성공 답변 merge는 동시 요청에서도 서로의 결과를 잃지 않는다.
4. 정확히 한 요청만 `IN_PROGRESS/READY → FINALIZING/FINALIZED` 전이를 획득한다.
5. `FeedbackResult`, `FeedbackTag`, finalization marker는 하나의 DB transaction으로 커밋된다.
6. `(attemptId, questionOrdinal)` 또는 동등한 DB unique가 중복 저장을 막는다.
7. 이미 완료된 finalize retry는 같은 결과 위치를 반환하고 새 행을 만들지 않는다.
8. 만료·finalize가 경합해도 완료 후 write가 들어오지 않는다.

**구현 전 결정**

- 진행 중 attempt도 DB에 저장할지, 별도 submission/finalization entity만 DB에 둘지
- 다중 인스턴스를 지원할지
- 외부 STT/LLM 호출 결과를 DB에 임시 저장할지

**필수 동시성/실패 테스트**

- 같은 attempt의 동시 finalize 2개
- feedback 저장 후 tag 저장 실패
- 서로 다른 question subset의 동시 answers
- answers와 finalize 경합
- finalize 성공 응답 유실 후 retry
- 만료와 제출 경합

제품 결과 복구와 필요한 영속 필드는 제품 감사 PC-12, PC-19를 함께 본다.

#### SCORE-01 — recency weight 방향

**수정 목표**

- 최신순 리스트의 index 0이 가장 큰 가중치를 갖게 한다.
- 정렬 방향과 weight 함수의 계약을 메서드명/주석/테스트로 고정한다.

**필수 테스트**

- 최신=5, 과거=1인 데이터의 가중 평균이 단순 평균보다 5에 가까움
- 최신=1, 과거=5인 반대 fixture
- 0/1/2개와 `MIN_FOR_WEIGHTED` 경계
- 요소/유형/콤보 계산이 같은 방향 사용

#### SCORE-02 — 평가 제외 점수

**수정 목표**

- TYPE_5~7의 MP 평가 제외를 숫자 0의 일반 점수와 구분한다. nullable/별도 flag/value object 중 하나로 표현한다.
- overall, 분석 평균, weakest 판정, grade 변환의 분모에서 제외한다.

**필수 테스트**

- MP 제외 + 나머지 네 항목 4점 → 평균 4.0 계열 결과
- 실제 유효 0점이 도메인에 존재한다면 제외와 구분
- 롤플레이를 많이 추가해도 MP 제외 때문에 등급이 구조적으로 하락하지 않음

#### DATA-02 — 회원 데이터 DB 무결성

**수정 순서**

1. 운영/fixture 데이터에 `(provider, providerId)`와 nickname 중복이 있는지 읽기 전용 점검
2. 중복 처리 정책 결정
3. entity annotation만이 아니라 DB unique constraint/migration 추가
4. OAuth 동시 가입을 재현해 하나의 회원만 생성됨을 검증

**완료 조건**

- 애플리케이션 check-then-insert 경합에도 DB가 중복을 막는다.
- constraint 예외가 로그인 500으로 노출되지 않고 기존 회원 조회/재시도로 수렴한다.

### 10.6 관리자·캐시 항목 완료 조건

#### ADMIN-01 — 존재하지 않는 템플릿 필드

**수정 목표**

- `QuestionSet`에 없는 `difficulty` 렌더링을 제거하거나 실제 view DTO에 존재하는 의미 있는 필드로 대체한다.
- 단순히 null-safe expression로 숨기지 않는다.

**필수 테스트**

- question set 0개/1개/여러 개 상태의 `/admin/question-sets` 렌더링이 200
- soft-deleted set 표시 정책 확인

#### ADMIN-02 — 출제 가능한 aggregate 관리

이 항목은 먼저 관리 계약을 결정해야 한다.

- 선택 A: question set 생성 요청이 필요한 TYPE 문항 전체를 포함하고 하나의 transaction으로 저장
- 선택 B: draft 상태를 명시하고 완성 전에는 출제 후보와 `findExistingTopics`에서 제외
- 선택 C: 질문 별 후속 API와 completeness validation 제공

**어떤 선택에도 필요한 완료 조건**

- 정상 관리자 API만 사용해 출제 가능한 세트를 만들 수 있다.
- 불완전 세트는 사용자 출제 후보로 보이지 않는다.
- required question type 누락을 관리자에게 설명한다.
- create/update/delete 후 캐시 일관성은 CACHE-01 기준을 만족한다.

#### CACHE-01 — 관리자 변경과 출제 캐시

**수정 목표**

- admin mutation service와 cache eviction을 같은 애플리케이션 경계에 둔다.
- JPA entity graph를 앱 수명 전체 보관하는 대신 immutable DTO snapshot 또는 bounded cache를 우선 검토한다.
- create/update/soft-delete가 다음 출제에 반영된다.

**필수 테스트**

1. topic을 한 번 조회해 cache warm-up
2. set/question 수정 후 새 내용 출제
3. soft-delete 후 삭제 set 미출제
4. 새 set 생성 후 후보 포함
5. transaction rollback 시 cache가 DB보다 앞서 오염되지 않음

#### DOMAIN-01 — 돌발 풀

제품 감사 PC-02를 source of truth로 사용한다. 완료 시 홈 돌발 버튼 후보가 `TopicCatalog.surpriseTopics()`와 DB 존재 set의 교집합인지 테스트한다.

### 10.7 테스트·성능·문서 항목 완료 조건

#### TEST-01 — 안전한 테스트 suite와 배포 gate

**수정 목표**

- 실제 Groq/외부 네트워크/개발 DB insert 테스트는 명시적 integration/manual tag나 profile로 기본 suite에서 분리한다.
- 구형 관리자 URL 테스트를 실제 JSON API 계약으로 갱신한다.
- 최소 `compile + unit/integration-safe tests`를 PR/배포 전에 실행한다.

**필수 검증**

- clean clone에서 필요한 로컬 전제조건이 문서화돼 있다.
- `./gradlew test`가 실제 유료 API를 호출하거나 임의 DB 데이터를 넣지 않는다.
- Docker/Testcontainers 필요 테스트는 명시적으로 분리되거나 CI에서 Docker와 함께 실행된다.
- 테스트 실패 시 deploy job이 실행되지 않는다.

#### TEST-02 — k6 재현 절차

**수정 목표**

- 추적 가능한 작은 audio fixture를 사용한다.
- 현재 attempt 생성 방식과 `/{attemptId}/answers` 경로를 반영한다.
- 설정 키를 현재 `LLM_ENABLED` 등 실제 application config와 맞춘다.

**필수 검증**

- clean clone의 문서 명령을 그대로 실행해 파일/route 단계에서 실패하지 않는다.
- mock AI와 live AI 결과를 구분해 라벨링한다.
- 성능 숫자의 payload, VU, duration, 환경, 외부 API 모드가 기록된다.

#### PERF-01 — 대용량 피드백 조회

**선행 결정**

- 분석: 전체 이력/최근 window/사전 집계 중 선택
- 기록: pagination/cursor
- 코칭: 현재 최근 30문항 규칙 유지 여부
- 시험 계획: 전체 이력이 정말 필요한지

**최소 완료 조건**

- 일반 화면 요청이 무제한 `TEXT` feedback graph를 메모리에 올리지 않는다.
- 목록 projection에는 필요한 필드만 조회한다.
- 1만 건 수준 fixture에서 query 수·조회 행·응답 메모리/시간을 회귀 검사한다.

#### DOC-01 — 완료 주장과 코드

이 항목은 원인 코드를 고친 뒤 마지막에 처리한다.

- README/CHANGELOG의 rate limit 경로와 실제 matcher 일치
- API 검증 완료 주장은 API-01/API-02 테스트가 통과할 때만 유지
- PROJECT 테스트 상태는 현재 suite를 실제 실행한 결과로 갱신
- 과거 CHANGELOG를 사실과 다르게 재작성하지 말고, 정정/후속 완료 항목을 새 줄로 남긴다.

### 10.8 날짜·DB·설계 항목 완료 조건

#### OPS-01 — 시간대

**수정 목표**

- 제품 기준 `Asia/Seoul`을 코드 또는 배포 설정에 명시한다.
- 날짜 계산 service에는 `Clock`을 주입해 테스트 가능하게 한다.
- DB `serverTimezone`만으로 JVM 날짜 문제가 해결됐다고 보지 않는다.

**필수 테스트**

- KST 00:00 직전/직후
- UTC와 KST 날짜가 다른 구간
- today progress, D-day, avoidance days가 같은 기준일 사용

#### DB-01 — schema migration

**선행 결정**

- Flyway 또는 Liquibase
- 이미 `ddl-auto=update`로 생성된 운영 DB를 어떤 baseline version으로 시작할지
- startup seed와 schema migration의 책임 분리
- rollback은 down migration이 아니라 forward fix/backup restore 중 무엇을 쓸지

**완료 조건**

- production은 `ddl-auto=update`에 의존하지 않는다.
- 빈 DB와 기존 baseline DB 모두 migration으로 목표 schema에 도달한다.
- 모든 schema 변경이 순서 있는 파일로 리뷰 가능하다.
- 애플리케이션 startup 실패와 migration 실패의 복구 절차가 문서화된다.

#### DESIGN-01 — 이중 source of truth

**선행 결정**

- 런타임 `ComboPattern`을 유일한 source of truth로 유지하고 영속 `Combo`/전략 bean을 제거할지,
- 영속 `Combo`를 실제 관리·출제 모델로 승격할지 결정한다.

**완료 조건**

- 출제 규칙의 source of truth가 코드와 `PROJECT.md`에서 하나다.
- 사용되지 않는 strategy bean이나 sample ID entity가 production bean graph에 남지 않는다.
- 영속 관계 제거 시 migration/data 영향이 처리된다.
- 모의고사/일반 콤보 패턴 테스트가 선택한 source를 직접 검증한다.

## 11. 문서 간 역할

- 이 문서: API·보안·데이터·운영·테스트 리스크의 근거와 기술 완료 조건
- [`product-contract-audit-2026-08-13.md`](product-contract-audit-2026-08-13.md): 사용자 여정, 화면 약속, 통계 단위, 제품 결정과 제품 acceptance criteria
- 루트 [`DOMAIN.md`](../DOMAIN.md): 바꾸기 전에 사람 확인이 필요한 OPIc 시험 규칙
- 루트 [`PROJECT.md`](../PROJECT.md): 현재 코드 지도. 감사 finding의 해결 상태를 대신하지 않음
- `docs/local/`: 과거 개인 조사 기록. 후속 작업 지시서나 현재 source of truth가 아님

이 목록은 침투 테스트 결과가 아니라 현재 커밋의 정적 감사 결과다. 각 항목을 수정할 때는 먼저 해당 최소 재현을 자동 테스트로 고정한 뒤 변경해야 한다.
