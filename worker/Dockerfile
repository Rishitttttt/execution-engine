# syntax=docker/dockerfile:1.7
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -pl worker -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
ARG DOCKER_GID=999
RUN addgroup -S app \
 && addgroup -S -g ${DOCKER_GID} docker \
 && adduser  -S -G app app \
 && addgroup app docker
USER app
COPY --from=build /src/worker/target/worker-*.jar /app/worker.jar
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD pgrep -f worker.jar > /dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/worker.jar"]
