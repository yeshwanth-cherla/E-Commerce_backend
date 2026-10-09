# ============================================================
# E-Commerce Platform — Spring Boot Production Dockerfile
# ============================================================
# Multi-stage Docker build for Java 17 Spring Boot JAR
# Compatible with Render, Railway, Fly.io, AWS App Runner
# ============================================================

# ─── Stage 1: Build Application Jar ─────────────────────────
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy dependency definition and download offline dependencies (cache layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production JAR
COPY src ./src
RUN mvn package -DskipTests -B

# ─── Stage 2: Runtime Image ──────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root system user for security
RUN addgroup -S ecommerce && adduser -S ecommerce -G ecommerce
USER ecommerce

# Copy compiled executable JAR from build stage
COPY --from=build /app/target/ecommerce-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose default HTTP port
EXPOSE 8080
ENV PORT=8080

# Execute Spring Boot application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
