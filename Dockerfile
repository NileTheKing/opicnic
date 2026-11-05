# CSS stage: 템플릿에서 쓰는 Tailwind 클래스만 뽑아 app.css를 만든다
FROM node:22-alpine AS css
WORKDIR /app
COPY package.json package-lock.json tailwind.config.js ./
COPY src/main/css src/main/css
COPY src/main/resources/templates src/main/resources/templates
RUN npm ci && npm run build:css

# Build stage
FROM gradle:8.13-jdk21 AS build
WORKDIR /app
COPY . .
COPY --from=css /app/src/main/resources/static/css/app.css src/main/resources/static/css/app.css
RUN ./gradlew bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# 기본 환경 변수 설정
ENV SPRING_PROFILES_ACTIVE=prod
# JVM 기본 시간대를 KST로. 이미지 기본은 UTC라 코드의 LocalDateTime.now()가 UTC가 되는데, 자동 저장 시각
# (@CreationTimestamp)은 DB 연결의 serverTimezone=Asia/Seoul을 거쳐 KST로 들어간다 — 둘을 비교하는 곳
# (게스트 일일 한도, 7일 정리, 결과 화면 경과 시간)이 9시간 어긋났다(2026-10-08 발견)
ENV TZ=Asia/Seoul

EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
