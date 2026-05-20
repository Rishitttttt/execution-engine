FROM node:20-bookworm-slim

# node:20-bookworm-slim ships with user `node` already at UID/GID 1000.
RUN apt-get update \
 && apt-get install -y --no-install-recommends time \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /work && chown 1000:1000 /work

USER 1000:1000
WORKDIR /work
ENTRYPOINT ["/bin/sh","-c"]
