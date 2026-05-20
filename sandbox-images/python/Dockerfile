FROM python:3.11-slim-bookworm

RUN apt-get update \
 && apt-get install -y --no-install-recommends time \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r runner --gid=1000 \
 && useradd  -r -g runner --uid=1000 --home-dir=/work --shell=/bin/sh runner \
 && mkdir -p /work && chown runner:runner /work

USER runner:runner
WORKDIR /work
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1

ENTRYPOINT ["/bin/sh", "-c"]
