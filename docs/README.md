# OPICnic Development Documentation

이 디렉토리는 "얼마나 자주 바뀌는가" 기준으로 문서를 나눈다. 새 문서를 추가할 때도 이 기준으로 위치를 정할 것.

| 파일 | 계층 | 갱신 규칙 |
|---|---|---|
| [`CHANGELOG.md`](CHANGELOG.md) | 완료 이력 | 기능 완료 시에만 추가. 과거 항목은 안 고침 |
| [`backlog.md`](backlog.md) | 활성 작업 | 진행 상황 바뀔 때마다 갱신 |
| [`deployment.md`](deployment.md) | 현재 인프라 | 배포 구조 바뀔 때만 갱신 |
| [`question-text-progress.md`](question-text-progress.md) | 진행 중 체크리스트 | 작업 끝나면 삭제 검토 |
| [`hold.md`](hold.md) | 보류 (기각 + 미구현 비전) | **현재 상태 아님.** 실제로 뭘 만들었는지는 CHANGELOG 참고 |
| `local/` | 개인 저널 (gitignore) | 절대 "진실"로 취급 안 함. 과거 결론이 나중에 반박될 수 있음 |
| `performance/` | 성능 조사 원본 (2026-04) | `local/`과 같은 성격이나 gitignore 이전에 커밋된 raw 증거. 결론은 README.md 엔지니어링 하이라이트로 이미 승격됨 |

루트의 `AGENTS.md`(협업 규약) + `DOMAIN.md`(OPIc 도메인 법칙) + `PROJECT.md`(코드베이스 지도)는 위 표와 별개로 매 세션 항상 읽어야 하는 최상위 문서 — 여기 두지 않는다.
