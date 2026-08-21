# Build context MUST be the repo root (order-platform), not this module's
# own directory — e.g. from the repo root:
#   docker build -f order-service/Dockerfile -t order-service .
# This is a multi-module Gradle build: settings.gradle declares all four
# subprojects, and Gradle's configuration phase evaluates every declared
# subproject's build.gradle even when only one subproject's task is
# requested (":order-service:bootJar" below) — so gradlew, the root
# settings.gradle/build.gradle, and every subproject's own build.gradle need
# to be present in the build context, not just this module's own files.
# Only order-service's actual src/ is copied, though: this project's
# no-shared-module architecture means no subproject's compileJava/bootJar
# task ever reads another subproject's source.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew .
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY order-service/build.gradle order-service/build.gradle
COPY inventory-service/build.gradle inventory-service/build.gradle
COPY payment-service/build.gradle payment-service/build.gradle
COPY search-indexer-service/build.gradle search-indexer-service/build.gradle
COPY order-service/src order-service/src

RUN chmod +x gradlew && ./gradlew :order-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/order-service/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
