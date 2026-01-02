# opicnic Project Context

## Project Overview

**opicnic** is a Spring Boot web application designed for two primary purposes:
1.  **OPIc Exam Preparation:** Provides practice functionality with AI-driven feedback using Google Vertex AI (Gemini) and Speech-to-Text (STT) capabilities.
2.  **Study Group Matching:** Facilitates the creation and management of study groups for various topics (Projects, Algorithms, Tech Stacks, Interviews).

## Tech Stack

*   **Language:** Java 21 (LTS)
*   **Framework:** Spring Boot 3.4.4
*   **Build Tool:** Gradle
*   **Database:** MySQL
    *   **Driver:** `com.mysql.cj.jdbc.Driver`
    *   **ORM:** Spring Data JPA (Hibernate)
*   **Frontend:** Thymeleaf (Server-side rendering)
*   **AI & ML:**
    *   **Spring AI:** Vertex AI Gemini (`gemini-1.5-flash-001`/`002`) - Optimized with Virtual Threads.
    *   **STT (Speech-to-Text):** Implemented using Spring `RestClient` to communicate with an external API (optimized for Virtual Threads).
*   **Security:** Spring Security with OAuth2 Client (Kakao Login)
*   **Testing:** JUnit 5, Testcontainers

## Key Features

### 1. OPIc Practice
*   **Practice Combos:** Logic for selecting question combos (`ComboPracticeService`, `ComboQuestionStrategy`).
*   **AI Feedback:** Uses `GeminiService` to analyze user responses and provide feedback.
*   **Speech Recognition:** `STTService` handles audio input processing using `RestClient`.

### 2. Study Group Management
*   **Study Posts:** Users can create posts to recruit members for study groups (`StudyPostController`, `StudyPostService`).
*   **Applications:** Users can apply to join groups, with status tracking (`PENDING`, `APPROVED`, `REJECTED`).
*   **Categories:** Supports various study types defined in `StudyType` (Project, Algorithm, etc.).

### 3. Member Management
*   **Authentication:** Social login via Kakao (`CustomOAuth2UserService`).
*   **My Page:** User profile and activity management (`MyPageController`).

## Configuration & Environment

Configuration is managed via `src/main/resources/application.yml` with support for profiles.

**Key Configuration Updates:**
*   **Java 21 Virtual Threads:** Enabled (`spring.threads.virtual.enabled: true`) for high-throughput I/O handling (Gemini API, STT API, DB).

*   **default/local:** Connects to `localhost:3306/opicnic`.
*   **dev:** Connects to `localhost:3306/opicnic`.
*   **prod:** Connects to an AWS RDS instance.

**Required Environment Variables (inferred):**
*   `DB_PASSWORD`: For production database access.
*   **Google Cloud Credentials:** Implicitly required for `spring-ai-vertex-ai-gemini`. Ensure `GOOGLE_APPLICATION_CREDENTIALS` is set or the environment is authenticated (e.g., via `gcloud auth application-default login`).

## Building and Running

### Prerequisites
*   JDK 21+
*   MySQL Server running on localhost (for default/dev profiles)

### Commands
*   **Build:** `./gradlew build`
*   **Run:** `./gradlew bootRun`
*   **Run Tests:** `./gradlew test`

## Development Conventions

*   **Architecture:** Standard Spring Boot Layered Architecture (Controller -> Service -> Repository -> Domain).
*   **DTO Pattern:** extensive use of DTOs (e.g., `StudyPostRequestDto`, `MemberDTO`) to decouple API/View from Domain entities.
*   **Async Processing:** configured in `AsyncConfig.java`.
*   **HTTP Client:** Prefer `RestClient` over `WebClient` or `RestTemplate` to leverage Virtual Threads efficiency.