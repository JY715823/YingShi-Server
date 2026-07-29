FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw mvnw
COPY mvnw.cmd mvnw.cmd
COPY pom.xml pom.xml
RUN chmod +x mvnw && ./mvnw -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN set -eux; \
    apt-get update; \
    for attempt in 1 2 3; do \
        if apt-get install -y --no-install-recommends --fix-missing ffmpeg curl; then \
            break; \
        fi; \
        if [ "$attempt" -eq 3 ]; then \
            exit 1; \
        fi; \
        rm -rf /var/lib/apt/lists/*; \
        apt-get update; \
        sleep 5; \
    done; \
    rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/yingshi-server-0.0.1-SNAPSHOT.jar app.jar

# Security: create non-root user
RUN groupadd --system --gid 1001 appgroup && \
    useradd --system --uid 1001 --gid appgroup appuser && \
    chown appuser:appgroup /app/app.jar
USER appuser

EXPOSE 8080

# Health check for container orchestrators
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -sf http://localhost:8080/actuator/health/liveness || exit 1

# JVM: use 75% of container memory, leave room for OS/ffmpeg
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
