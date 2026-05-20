FROM gcc:13-bookworm

RUN apt-get update \
 && apt-get install -y --no-install-recommends time \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r runner --gid=1000 \
 && useradd -r -g runner --uid=1000 --home-dir=/work --shell=/bin/sh runner \
 && mkdir -p /work && chown runner:runner /work

USER runner:runner
WORKDIR /work
ENTRYPOINT ["/bin/sh","-c"]
