# Feature Backlog

구현 예정이거나 고도화할 기능 목록.

---

## Practice Attempt / Feedback Retry

### Done

- `PracticeAttempt` 도입
- Caffeine 기반 `PracticeAttemptStore` 구현
- `attemptId -> questionIds/memberId/mode/status/expiresAt` 저장
- 제출/재시도 시 클라이언트가 보낸 question content를 신뢰하지 않고 `attemptId`로 서버에서 문제 복원
- 서버의 동일 `InputStream` 자동 재시도 제거
- 실패 문항만 브라우저가 보관 중인 녹음 Blob으로 재제출
- 제출 API 분리
- 재시도 API 분리
- finalize API 분리
- 결과 페이지 이동 전 `beforeunload` 이탈 경고 추가

### Current Shape

```text
문제 시작:
GET /practice/combo
GET /practice/mock
-> Thymeleaf Controller가 questions + attemptId 생성
-> question.html 렌더링

답변 제출:
POST /api/practice-attempts/answers

실패 문항 재제출:
POST /api/practice-attempts/answers/retry

결과 확정:
POST /api/practice-attempts/answers/finalize

결과 화면:
GET /practice/feedback/result
```

현재 구조는 `시작/결과 화면 = Thymeleaf`, `제출/재시도/finalize = API`인 중간 단계다.

### Next

- API 응답 DTO 정리
- `HttpServletRequest#getParameter`, `getParts` 직접 파싱 제거
- 결과 누적용 session 제거 여부 결정
- `resultId` 기반 결과 조회 구조 검토
- IndexedDB에 녹음 Blob 임시 저장
- React 전환 시 `POST /api/practice-attempts` 시작 API 연결
- Caffeine store를 Redis store로 교체 가능한 구조 유지

---

## OPIc Mock Exam

### Done

- 난이도별 런타임 `ComboPattern` 적용
- Level 3~4: `[123] [123] [134] [674] [15]`
- Level 5~6: `[123] [134] [134] [678] [910]`
- 선택 주제 3개 + 돌발 후보 2개 배치
- 돌발 슬롯 랜덤화
- `ComboPattern.order` 제거
- 지원 주제/주제 그룹 `TopicCatalog` 공통화
- DB에 `QuestionSet`이 있는 주제만 후보로 사용
- 후보 부족 시 같은 주제를 반복하지 않고 모의고사 시작 차단

### Next

- 진짜 돌발 주제 문제은행 추가
- 선택 주제 pool과 돌발 주제 pool 분리
- 기존 `Combo` 엔티티/전략 계열 제거 여부 결정
- `QuestionSet`이 `TYPE_1~TYPE_10`을 모두 갖는지 관리자 저장 시점 또는 테스트에서 검증
- 잘못된 topic/difficulty URL 파라미터 예외 처리
