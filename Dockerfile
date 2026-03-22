# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

WORKDIR /app

# Copy gradle files first for dependency caching
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
COPY mongo-fields-annotations/build.gradle mongo-fields-annotations/build.gradle
COPY mongo-fields-processor/build.gradle mongo-fields-processor/build.gradle

RUN chmod +x gradlew

# Download dependencies (cached unless build.gradle changes)
RUN --mount=type=cache,target=/root/.gradle \
    GITHUB_ACTOR=${GITHUB_ACTOR} GITHUB_TOKEN=${GITHUB_TOKEN} \
    ./gradlew dependencies --no-daemon

# Copy source and build
COPY src src
COPY mongo-fields-annotations/src mongo-fields-annotations/src
COPY mongo-fields-processor/src mongo-fields-processor/src

RUN --mount=type=cache,target=/root/.gradle \
    GITHUB_ACTOR=${GITHUB_ACTOR} GITHUB_TOKEN=${GITHUB_TOKEN} \
    ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

COPY --from=builder /app/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/v1/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
