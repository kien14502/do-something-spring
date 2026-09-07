# syntax=docker/dockerfile:1
# Build context là thư mục gốc repository:
#   docker build -f development/Dockerfile -t do-something:local .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Tách bước tải dependency thành layer riêng để cache khi chỉ src/ thay đổi.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
# Test cần PostgreSQL/Redis/Kafka nên không chạy trong image build; xem CI riêng.
RUN mvn -B -ntp -DskipTests clean package \
    && mv target/do-something-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar app.jar
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
