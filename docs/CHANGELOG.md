# Changelog

완료된 기능/변경 이력. 과거 항목은 안 고친다 — 나중에 방향이 바뀌어도 여기 기록은 "그 시점에 그렇게 했다"는 사실 그대로 남긴다. 지금 뭘 만들었는지 현재 상태가 궁금하면 README.md/AGENTS.md/DOMAIN.md를 먼저 보고, 여기는 "언제 뭘 했는지" 타임라인이 필요할 때 참고.

- OPIc 출제 알고리즘 (Topic & Difficulty 기반 랜덤 콤보) 구현
- Groq Whisper 기반 STT 연동 (초기 Gemini → Groq 마이그레이션)
- Groq llama-3.3-70b 기반 피드백 엔진 구축 (Spring AI OpenAI starter 호환), 이후 llama-4-scout로 교체
- Java 21 Virtual Threads + StructuredTaskScope 병렬 처리 (per-subtask 재시도 포함)
- Bucket4j Rate Limiting (사용자 ID 기반, 10회/시간)
- FeedbackResult DB 저장 (Member 연관, 성공한 피드백만 저장)
- 피드백 실패 시 부분 결과 반환 (전체 오류 대신 실패 카드 표시)
- Oracle Cloud + ~~DuckDNS + Let's Encrypt~~ **Cloudflare + Origin Certificate** + Nginx 배포 완료 (2026-06-04). DuckDNS는 Let's Encrypt SERVFAIL 반복으로 포기, Cloudflare로 전환
- dev/prod 프로파일 분리 + 모바일 반응형 (2026-06-06)
- `restoreQuestionsForIndexes` ConcurrentHashMap 캐싱 + `@Transactional(readOnly=true)` 제거 → start p95 16.69s→20ms, answers p95 20.5s→3.73s (2026-06-11)
- Feedback 점수 필드 6개 + 한국어 전용 프롬프트 (2026-06-11)
- 돌발 주제 23개 전용 풀 분리 + QuestionSet 데이터 완성 + MockExamService 정합화 (2026-06-12)
- 주제탐색 카테고리화 UI + 토픽 토글 즉시 반영 (2026-06-12)
- 코칭 리포트 자유텍스트 → 태그 기반 아키텍처 재설계 (자기모순 리포트 문제 해결) (2026-07)
- Study board(스터디 게시판) 기능 제거 — 사용되지 않던 기능 (2026-07)
- Admin 질문세트 CRUD, PracticeAttempt 엔드포인트를 REST 원칙에 맞게 정비 (리소스 경로, 공통 에러 응답, 검증) (2026-07)
- 유형별 연습 모드(`/practice/type`)가 이미 구현·동작 중이었다는 게 뒤늦게 확인됨 — `docs/hold.md`/`docs/backlog.md`에 "미구현"으로 잘못 남아있던 걸 정정 (2026-07-15)
- 학습관리 재설계: `/today`(오늘 할 일) 신규 화면 — 오늘 콤보 진행률(attemptId 기반 정확 집계), 이번 주 과제 자기신고 체크박스, D-day 연동 회피 감지. 홈/학습분석에 코칭 티저 위젯 추가 (2026-07-15)
