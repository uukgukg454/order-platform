# Build context MUST be the repo root (order-platform), not this module's
# own directory — e.g. from the repo root:
#   docker build -f search-indexer-service/Dockerfile -t search-indexer-service .
# See order-service/Dockerfile's own comment for why the whole multi-module
# tree (minus sibling src/ directories) needs to be in the build context.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY order-service/build.gradle order-service/build.gradle
COPY inventory-service/build.gradle inventory-service/build.gradle
COPY payment-service/build.gradle payment-service/build.gradle
COPY search-indexer-service/build.gradle search-indexer-service/build.gradle
COPY search-indexer-service/src search-indexer-service/src

RUN chmod +x gradlew && ./gradlew :search-indexer-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/search-indexer-service/build/libs/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
