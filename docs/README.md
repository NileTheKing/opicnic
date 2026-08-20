# OPIcnic Development Documentation

이 디렉토리는 "얼마나 자주 바뀌는가" 기준으로 문서를 나눈다. 새 문서를 추가할 때도 이 기준으로 위치를 정할 것.

| 파일 | 계층 | 갱신 규칙 |
|---|---|---|
| [`CHANGELOG.md`](CHANGELOG.md) | 완료 이력 | 기능 완료 시에만 추가. 과거 항목은 안 고침 |
| [`backlog.md`](backlog.md) | 활성 작업 | 진행 상황 바뀔 때마다 갱신 |
| [`deployment.md`](deployment.md) | 현재 인프라 | 배포 구조 바뀔 때만 갱신 |
| [`codebase-risk-audit-2026-08-13.md`](codebase-risk-audit-2026-08-13.md) | 감사 + 작업 인계 | API·보안·데이터·테스트 리스크의 근거, 재현, 작업 준비도, 기술 완료 조건 (애플리케이션 코드 수정 없음) |
| [`product-contract-audit-2026-08-13.md`](product-contract-audit-2026-08-13.md) | 감사 + 작업 인계 | 가입부터 오늘 할 일까지 제품 계약 불일치, 재현, 수정 경계, 선행 결정, acceptance criteria (애플리케이션 코드 수정 없음) |
| [`audit-followup-spec-2026-08-20.md`](audit-followup-spec-2026-08-20.md) | 활성 감사 후속 명세 | 2026-08-19 수정 재리뷰에서 남은 6건의 최소 설계, 실패 fixture, 완료 조건, 범위 밖 |
| [`question-text-progress.md`](question-text-progress.md) | 진행 중 체크리스트 | 작업 끝나면 삭제 검토 |
| [`hold.md`](hold.md) | 보류 (기각 + 미구현 비전) | **현재 상태 아님.** 실제로 뭘 만들었는지는 CHANGELOG 참고 |
| `local/` | 개인 저널 (gitignore) | 절대 "진실"로 취급 안 함. 과거 결론이 나중에 반박될 수 있음 |
| `performance/` | 성능 조사 원본 (2026-04) | `local/`과 같은 성격이나 gitignore 이전에 커밋된 raw 증거. 결론은 README.md 엔지니어링 하이라이트로 이미 승격됨 |

루트의 `AGENTS.md`(협업 규약) + `DOMAIN.md`(OPIc 도메인 법칙) + `PROJECT.md`(코드베이스 지도)는 위 표와 별개로 매 세션 항상 읽어야 하는 최상위 문서 — 여기 두지 않는다.

## 후속 에이전트가 읽는 순서

감사 항목을 실제 수정하는 작업이라면 다음 순서를 따른다.

1. `AGENTS.md`, `DOMAIN.md`, `PROJECT.md`
2. 맡은 영역의 감사 문서
   - 기술/API/보안/운영: `codebase-risk-audit-2026-08-13.md`
   - 제품 흐름/화면/통계 의미: `product-contract-audit-2026-08-13.md`
3. DATA-01/SCORE-02/API-01/ADMIN-02/AI-01/TEST-02 후속 수정이면 `audit-followup-spec-2026-08-20.md`
4. 감사 문서에서 연결한 현재 소스와 테스트
5. `READY / MIXED / DECISION` 상태 및 선행 결정 확인
6. 재현 테스트 → 최소 수정 → acceptance criteria 검증 → 문서 상태 갱신

두 감사 문서는 짧은 현황 요약이 아니라 작업 인계서다. finding의 원인·영향만 읽고 임의로 구현하지 말고 각 문서의 “후속 구현 에이전트 인계”, “먼저 사람이 결정해야 하는 제품 정책”, “완료 조건”을 함께 따른다.

반대로 아래 자료는 단독 작업 지시서가 아니다.

- `CHANGELOG.md`: 이미 완료됐다고 기록한 이력
- `hold.md`: 기각 또는 보류한 선택지
- `local/`: 개인 조사·실험 기록
- `performance/`: 과거 raw 측정 증거

이 자료에서 아이디어나 수치를 발견해도 현재 코드와 감사 문서로 다시 확인한 뒤 작업해야 한다.
