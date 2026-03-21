# ============================================================
# Multi-stage Dockerfile for Spring Boot services
# Optimized for fast builds and small images
# Java 21 + layered JAR for optimal Docker caching
# ============================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom first (cached layer - only rebuilds if deps change)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ../../pom.xml* ./

# Download dependencies (cached unless pom changes)
RUN ./mvnw dependency:go-offline -B 2>/dev/null || true

# Copy source
COPY src ./src

# Build, skip tests in Docker build (tests run in CI)
RUN ./mvnw package -DskipTests -B && \
    java -Djarmode=layertools -jar target/*.jar extract

# Stage 2: Runtime image - minimal
FROM eclipse-temurin:21-jre-alpine

# Security: run as non-root
RUN addgroup -S nexushub && adduser -S nexushub -G nexushub

WORKDIR /app

# Copy layered JAR - maximizes Docker layer caching
# Dependencies layer changes least often
COPY --from=builder /app/dependencies/ ./
COPY --from=builder /app/spring-boot-loader/ ./
COPY --from=builder /app/snapshot-dependencies/ ./
COPY --from=builder /app/application/ ./

# Set ownership
RUN chown -R nexushub:nexushub /app

USER nexushub

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+HeapDumpOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
