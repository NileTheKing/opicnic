# opicnic Project Handover Context (2026-01-12)

## 🎯 Current Status
- **Project Goal:** AI-powered OPIc tutor with personalized feedback.
- **Key Positioning:** AI-Native Backend Engineer (focusing on JVM optimization and AI integration).
- **Milestone:** Migration to Google GenAI (API Key) is complete, and monitoring infrastructure is set up.

## 🛠 Tech Stack
- **Backend:** Java 21 (Virtual Threads), Spring Boot 3.4.4, Spring AI 1.1.2.
- **AI Model:** Gemini 2.0 Flash-Lite.
- **Infra:** Docker Compose (MySQL, STT, Prometheus, Grafana).
- **Testing:** k6 (load testing), MockChatModel (cost-free testing).

## 🔑 Recent Changes (Refactored on Jan 7, 2026)
1. **AI Migration:** Switched to `spring-ai-starter-model-google-genai` for API Key support.
2. **Logic Refactoring:** `ComboQuestionStrategy` now returns a single random `Optional<Combo>`.
3. **Optimizations:** 
   - `WebClient` -> `RestClient` for Virtual Thread efficiency.
   - `QuestionDto.from` static factory method implemented.
4. **Monitoring:** Integrated Prometheus/Grafana to observe JVM metrics and container resources.

## 🚀 Next Action Plan
1. **Load Testing:** Run `k6 run scripts/load-test.js` with `ai.gemini.enabled: false`.
2. **JVM Analysis:** Perform Thread/Heap Dump analysis under load to detect Pinning issues.
3. **MCP Integration:** Build MCP server to allow AI to access backend APIs directly.
4. **Data Pipeline:** Link Kafka and Elasticsearch for real-time KPI visualization.

## 📂 Documentation Index
- `/docs/product/ideas.md`: Vision and Snack-sized learning concept.
- `/docs/tech/architecture.md`: System design and AI integration details.
- `/docs/roadmap/milestones.md`: Future goals including Voice Cloning.
