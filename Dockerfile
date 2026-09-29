# ── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# Copy Gradle wrapper and dependency descriptors first for layer caching
COPY gradlew gradlew
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts gradle.properties* ./
COPY gradle/libs.versions.toml gradle/libs.versions.toml

# Module build files
COPY shared/build.gradle.kts shared/
COPY backend/build.gradle.kts backend/
COPY app/build.gradle.kts app/

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath -q 2>/dev/null || true

# Copy sources
COPY shared/src/ shared/src/
COPY backend/src/ backend/src/

# Build fat jar (includes :shared)
RUN ./gradlew --no-daemon :backend:jar -x test

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /build/backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
