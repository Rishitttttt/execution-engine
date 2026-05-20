FROM eclipse-temurin:21-jdk-alpine

RUN apk add --no-cache coreutils \
 && addgroup -S -g 1000 runner \
 && adduser  -S -G runner -u 1000 -h /work -s /bin/sh runner \
 && mkdir -p /work && chown runner:runner /work

USER runner:runner
WORKDIR /work

ENTRYPOINT ["/bin/sh", "-c"]
