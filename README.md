# Opicnic

OPIc 시험 대비 AI 모의고사 및 피드백 서비스 백엔드 프로젝트입니다. Java 21의 가상 스레드(Virtual Threads)와 로우 레벨 I/O 최적화를 통해 대규모 음성 데이터 처리 성능을 확보하는 데 집중했습니다.

## 주요 기능
- **OPIc 콤보 문제 출제**: 주제 및 난이도별 2~3개 연속 질문 자동 생성 로직
- **AI 기반 음성 피드백**: 사용자의 음성 답변을 STT로 변환 후 LLM(Gemini) 기반 평가 리포트 제공
- **I/O 파이프라인 최적화**: 가상 스레드와 인메모리 스트리밍을 통한 대용량 데이터 처리 병목 해소

## 기술 스택
- **Language**: Java 21
- **Framework**: Spring Boot 3.4
- **Database**: MySQL, Spring Data JPA
- **AI & ML**: Google Gemini (LLM), OpenAI Whisper (STT)
- **Infrastructure**: Docker, Python FastAPI (STT Worker)

## 실행 방법
1. 도커 컴포즈 실행 (DB, STT 서버)
   ```bash
   docker-compose up -d
   ```
2. 스프링 부트 애플리케이션 실행
   ```bash
   ./gradlew bootRun
   ```

## 관련 문서
- [성능 최적화 리포트](docs/private/PORTFOLIO_MIDAS.md)
- [병목 분석 및 해결 일지](docs/performance/INVESTIGATION_LOG.md)
- [성능 테스트 재현 가이드](docs/performance/REPRODUCTION_GUIDE.md)
