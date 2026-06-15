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
        if apt-get install -y --no-install-recommends --fix-missing ffmpeg; then \
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
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
