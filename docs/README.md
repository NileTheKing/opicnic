# OPIcnic Development Documentation

문서는 두 종류다. **상시 문서**는 현재 상태를 말하고 계속 갱신된다. **시점 문서**는 어느 날의 감사·명세·측정이고, 그 작업이 닫히면 `archive/`로 간다 — 삭제하지 않되 활성 위치에 두지도 않는다. 문서마다 "태어나는 규칙"만 있고 "은퇴 규칙"이 없어서 1년치 산출물이 다 활성처럼 보였던 것(2026-09-18 정리)이 이 구분의 이유다.

## 상시 문서

| 파일 | 역할 | 갱신 규칙 |
|---|---|---|
| [`adr/`](adr/README.md) | 아키텍처 결정 기록 | 결정할 때 추가. 뒤집히면 새 ADR + 옛 것 상태 변경. 본문은 안 고침 |
| [`backlog.md`](backlog.md) | **열린** 작업만 | 상태 바뀔 때마다. 끝난 건 CHANGELOG 한 줄로 옮기고 여기서 지움 |
| [`CHANGELOG.md`](CHANGELOG.md) | 완료 이력 | 완료 시 한 줄 추가. 과거 항목은 안 고침(링크 경로 제외) |
| [`deployment.md`](deployment.md) | 현재 인프라·운영 | 배포 구조 바뀔 때만 |
| [`hold.md`](hold.md) | 기각·보류한 선택지 | **현재 상태 아님.** 다시 검토될 때 ADR로 승격 |
| [`performance/slo.md`](performance/slo.md) | 비동기 전환 검증 기준 | SLO 6개, 시나리오 S1~S4, 전/후 표. ADR-0001이 "왜"라면 이건 "됐는지 뭘로 아나" |
| `performance/<날짜>-*.md`, `performance/<날짜>/` | 측정 발견·산출물 | 실행마다 추가. 결론은 ADR·slo.md·README로 승격하고 원본은 여기 |
| `local/` | 개인 저널 (gitignore) | 진실로 취급 안 함 |

루트의 `AGENTS.md`(협업 규약) + `DOMAIN.md`(OPIc 도메인 법칙) + `PROJECT.md`(코드베이스 지도)는 매 세션 항상 읽는 최상위 문서 — 여기 두지 않는다.

## 지금 작업을 이어받는다면 읽는 순서

1. `AGENTS.md`, `DOMAIN.md`, `PROJECT.md`
2. [`adr/0001-async-r2.md`](adr/0001-async-r2.md) — 왜 R2 + 비동기인가
3. [`performance/slo.md`](performance/slo.md) — 뭘 재서 됐다고 할 것인가, 전/후 표 현황
4. [`backlog.md`](backlog.md) 맨 아래 섹션 — 다음 순서
5. `performance/2026-09-11-gc-pressure-finding.md`, `performance/2026-09-18/` — 최근 측정 원본

## archive/

닫힌 작업의 시점 문서. 읽어도 되지만 **현재 상태로 취급하지 않는다** — 거기 적힌 finding·수치·"미작업"은 그 날짜 기준이다.

| 파일 | 무엇이었나 | 닫힌 이유 |
|---|---|---|
| [`codebase-risk-audit-2026-08-13.md`](archive/codebase-risk-audit-2026-08-13.md) | API·보안·데이터·테스트 리스크 감사 + 인계서 (1,034줄) | 1단계 8건·READY 5건 수정 완료(CHANGELOG 08-13). 남은 2건은 backlog |
| [`product-contract-audit-2026-08-13.md`](archive/product-contract-audit-2026-08-13.md) | 가입~오늘 할 일 제품 계약 불일치 감사 (1,216줄) | 위와 같이 처리됨 |
| [`audit-followup-spec-2026-08-20.md`](archive/audit-followup-spec-2026-08-20.md) | 재리뷰 후 남은 6건 명세 | 4건 완료(CHANGELOG 08-20), FU-01/FU-05는 backlog에 |
| [`multipart-bytearray-cleanup-spec-2026-08-21.md`](archive/multipart-bytearray-cleanup-spec-2026-08-21.md) | `InputStream`→`byte[]` 타입 정리 인계서 | 구현 완료. 배경은 README.md 엔지니어링 하이라이트에 |
| [`question-text-progress.md`](archive/question-text-progress.md) | DataInitializer 문제 텍스트 교체 체크리스트 | 체크리스트만 안 갱신됐을 뿐 텍스트는 전부 교체됨(2026-09-18 확인) |
