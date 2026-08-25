# ── Build stage ───────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies (layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build jar
RUN mvn clean package -DskipTests

# ── Runtime stage ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Create media uploads directory
RUN mkdir -p /app/media-uploads

# Copy jar from builder stage
COPY --from=builder /app/target/file-*.jar app.jar

EXPOSE 8031

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8031/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-Dspring.devtools.restart.enabled=false", \
            "-Dspring.devtools.livereload.enabled=false", \
            "-jar", "app.jar"]