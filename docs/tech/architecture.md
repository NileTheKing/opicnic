# Technical Architecture

## 🛠 Tech Stack
- **Backend:** Java 21 (Virtual Threads 활성화), Spring Boot 3.4.4
- **Database:** MySQL 8.0 (Docker 기반)
- **AI Model:** Gemini 2.0 Flash-Lite (API Key 방식)
- **HTTP Client:** Spring RestClient (Synchronous but Virtual Thread friendly)

## 🏗 System Components

### 1. AI 통합 구조 (MCP)
- **MCP (Model Context Protocol):** AI가 백엔드 API를 도구(Tool)로 직접 호출하는 구조.
- **에이전트화:** AI가 사용자 학습 이력을 조회하여 "지난주 대비 성장 지표"를 직접 리포팅함.

### 2. 데이터 파이프라인
- **로그 수집:** 사용자 답변 및 학습 로그 수집 (Kafka).
- **분석 및 시각화:** Elasticsearch(ES)에 적재 후 KPI 대시보드 구축.
- **ETL:** 가공된 데이터를 바탕으로 취약 유형 분석 및 맞춤 문제 추천.

### 3. STT (Speech-to-Text)
- **Current:** Python FastAPI + OpenAI Whisper (Local Docker).
- **Future:** Gemini Native Audio Input (Multimodal) 전환을 통한 아키텍처 단순화.
