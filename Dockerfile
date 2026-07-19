# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy the Gradle wrapper + build scripts first so dependency resolution is cached
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Copy sources and build the executable jar (tests are run separately in CI)
COPY src src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# OS 타임존도 KST로 맞춘다(앱은 코드에서 한 번 더 고정 — BackendApplication 참고).
ENV TZ=Asia/Seoul
RUN apt-get update \
 && apt-get install -y --no-install-recommends tzdata \
 && ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
 && rm -rf /var/lib/apt/lists/*

# Run as an unprivileged user
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
