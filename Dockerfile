# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy gradle files first for dependency caching
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

RUN chmod +x gradlew

# Download dependencies (cached unless build.gradle changes)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon

# Copy source and build
COPY src src

RUN --mount=type=cache,target=/root/.gradle \
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
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/v1 || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
