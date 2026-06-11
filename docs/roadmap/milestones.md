# Roadmap & Milestones

## 🚀 Phase 1: MVP (Completed)
- OPIc 출제 알고리즘 (Topic & Difficulty 기반 랜덤 콤보) 구현.
- Groq Whisper 기반 STT 연동 (초기 Gemini → Groq 마이그레이션).
- Groq llama-3.3-70b 기반 피드백 엔진 구축 (Spring AI OpenAI starter 호환).
- Java 21 Virtual Threads + StructuredTaskScope 병렬 처리 (per-subtask 재시도 포함).
- Bucket4j Rate Limiting (사용자 ID 기반, 10회/시간).
- FeedbackResult DB 저장 (Member 연관, 성공한 피드백만 저장).
- 피드백 실패 시 부분 결과 반환 (전체 오류 대신 실패 카드 표시).
- Oracle Cloud + ~~DuckDNS + Let's Encrypt~~ **Cloudflare + Origin Certificate** + Nginx 배포 완료 (2026-06-04). DuckDNS는 Let's Encrypt SERVFAIL 반복으로 포기, Cloudflare로 전환.

## 📈 Phase 2: System Integration & Data Driven
- **MCP Server 도입:** 백엔드 API와 AI의 유기적 결합 (자율 에이전트화).
- **데이터 파이프라인:** 사용자 로그 수집 및 KPI 시각화 (Kafka, ES).
- **개인화 추천:** 유저 데이터 분석을 통한 취약 유형 집중 문제 추천.

## 🌟 Phase 3: Future & Premium Features
- **멀티모달 전환:** Gemini Native Audio Input을 사용하여 음성 뉘앙스(감정, 속도) 분석.
- **AI 발음/억양 코칭:** 오디오 파형 분석을 통한 단어/문장 레벨의 정밀 발음 교정.
- **Voice Cloning:** 유료 사용자를 위한 '내 목소리로 듣는 모범 답안' 기능.
- **모바일 확장:** 웹을 넘어 모바일 앱 환경에서 언제 어디서나 스낵형 학습 제공.
