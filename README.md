# OCEE — Online Code Execution Engine

## Demo

[![Watch the demo](https://cdn.loom.com/sessions/thumbnails/25e76c15b07a410283c46ba8e6ae518b-with-play.gif)](https://www.loom.com/share/25e76c15b07a410283c46ba8e6ae518b)

---

Multi-language code-judge service: submit source code via REST, run it in a hardened Docker sandbox, get back stdout/stderr, exit code, and real CPU/memory metrics. Judge0-inspired contract, but not drop-in compatible.

Supported languages: **Python 3.11, C (gcc 13), C++ (g++ 13), Java 21, Node 20**.

## How it works

```
                         ┌─────────────────────────────────────────────┐
                         │               OCEE Platform                  │
                         │                                              │
  ┌────────┐  POST       │  ┌──────────────────────────────────────┐   │
  │ Client │────────────▶│  │           API  (Spring Boot)         │   │
  │        │◀────────────│  │                                      │   │
  └────────┘  token +    │  │  • REST endpoints (/api/submissions) │   │
              result      │  │  • Idempotency (key + body hash)    │   │
                         │  │  • Webhook delivery w/ retry         │   │
                         │  │  • ?wait=true long-poll support      │   │
                         │  └───────────┬──────────────────────────┘   │
                         │              │                               │
                         │     ┌────────▼────────┐                     │
                         │     │   PostgreSQL     │                     │
                         │     │                 │                     │
                         │     │ • submissions   │                     │
                         │     │ • outbox        │                     │
                         │     │ • webhooks      │                     │
                         │     └─────────────────┘                     │
                         │                                              │
                         │     ┌─────────────────┐                     │
                         │     │      Redis       │                     │
                         │     │                 │                     │
                         │     │ ocee.jobs   ───────────────────────┐  │
                         │     │ ocee.results◀──────────────────┐   │  │
                         │     └─────────────────┘              │   │  │
                         │                                       │   │  │
                         │  ┌────────────────────────────────────│───▼─┐│
                         │  │        Worker  (Spring Boot)       │   │ ││
                         │  │                                    ▼   │ ││
                         │  │  pulls job ──▶ spawn sandbox ──▶ result ││
                         │  └────────────────────────────────────────┘│
                         │                                              │
                         │  ┌─────────────────────────────────────┐    │
                         │  │        Sandbox Containers            │    │
                         │  │                                      │    │
                         │  │  ┌─────────┐ ┌──────┐ ┌────────┐   │    │
                         │  │  │ Python  │ │  C/  │ │  Java  │   │    │
                         │  │  │  3.11   │ │ C++  │ │   21   │   │    │
                         │  │  └─────────┘ └──────┘ └────────┘   │    │
                         │  │  ┌─────────┐                        │    │
                         │  │  │  Node   │  read-only fs          │    │
                         │  │  │   20    │  network=none          │    │
                         │  │  └─────────┘  non-root UID 1000     │    │
                         │  └─────────────────────────────────────┘    │
                         └─────────────────────────────────────────────┘
```

**Execution flow:**
1. Client POSTs source code + language → API writes to Postgres outbox + publishes to `ocee.jobs`
2. Worker picks job from Redis stream → compiles (if needed) + runs in isolated container
3. Container has no network, read-only rootfs, CPU/memory/pid limits enforced
4. Worker publishes result to `ocee.results` → API updates submission row
5. Client polls token or receives webhook callback

## Architecture

```
        ┌──────────┐     POST /api/submissions     ┌─────────┐
client ─▶│   api    │──────────────────────────────▶│ postgres│
        │ (Spring) │  outbox row + Redis publish   │         │
        └────┬─────┘                                └─────────┘
             │ Redis Streams: ocee.jobs                 ▲
             ▼                                          │ result row
        ┌──────────┐  docker run --read-only            │
        │  worker  │──────────────────────────────▶ sandbox container
        │ (Spring) │  capture stdout/stderr/RSS         │
        └────┬─────┘                                    │
             │ Redis Streams: ocee.results              │
             └──────────────────────────────────────────┘
```

- **api** — REST surface, persistence (Postgres + Flyway), idempotency (key + body hash), webhook delivery with retry + dead-letter, in-memory `WaitRegistry` for `?wait=true` callers.
- **worker** — pulls jobs from `ocee.jobs`, runs them in a per-language sandbox image, publishes results to `ocee.results`. No DB access.
- **common** — shared `JobMessage` / `JobResult` DTOs.
- **sandbox-images/** — minimal Debian/Alpine images per language; non-root `runner` user (UID 1000), GNU `time` for metrics.

## Quick start

Requires Docker (with current user in `docker` group) and Java 21 + Maven 3.9 for development.

```bash
# 1. Build the sandbox images (once, ~2 min)
mvn -pl worker pre-integration-test

# 2. Bring up the full stack
DOCKER_GID=$(getent group docker | cut -d: -f3) docker compose up -d

# 3. Smoke test
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8080/api/languages | jq

curl -s -X POST 'http://localhost:8080/api/submissions?wait=true' \
  -H 'Content-Type: application/json' \
  -d '{"language_id":1,"source_code":"print(2+2)"}' | jq

# 4. Tear down
docker compose down          # keeps postgres data
docker compose down -v       # also wipes the volume
```

`DOCKER_GID` must match the host's `docker` group GID — the worker container needs r/w access to the mounted `/var/run/docker.sock` to spawn sandbox containers. The default in `docker-compose.yml` is `999`; override only if your host differs.

## API

All routes are under `/api`. Errors are RFC 7807 `application/problem+json`.

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/languages` | List active languages |
| `GET` | `/api/languages/{id}` | One language |
| `POST` | `/api/submissions` | Create submission. Query: `?wait=true&timeout=N` (server caps `timeout` at 10s) |
| `GET` | `/api/submissions/{token}` | Fetch by token |
| `GET` | `/api/submissions` | Cursor-paginated list. Query: `?status=...&cursor=...` |
| `GET` | `/api/healthz`, `/api/readyz` | Liveness / readiness |
| `GET` | `/actuator/health`, `/actuator/prometheus` | Spring Boot actuator |

### Submission body

```json
{
  "language_id": 1,
  "source_code": "print(2+2)",
  "stdin": "",
  "expected_output": "4\n",          // optional, enables WA comparison
  "callback_url": "https://...",     // optional, fires webhook on completion
  "cpu_time_limit": 2.0,
  "memory_limit": 128000
}
```

Pass an `Idempotency-Key` header for safe retries: same key + same body → same submission token; same key + different body → 409 Conflict.

### Status codes (see `language` table for exact ids)

- `In Queue`, `Processing` — pre-result
- `Accepted` — exit 0, optionally output matched expected
- `Wrong Answer` — exit 0 but output mismatch (only when `expected_output` set)
- `Compile Error`, `Runtime Error (NZEC | SIGSEGV | ...)`
- `Time Limit Exceeded`, `Memory Limit Exceeded`
- `Internal Error`

### Webhooks

If `callback_url` is set, the api enqueues a `WebhookDelivery` row when results land. A scheduled drainer POSTs the submission JSON with headers:

- `X-OCEE-Submission-Token`
- `X-OCEE-Delivery-Attempt` (1..5)

Backoff: `1s → 5s → 30s → 5m → 5m`. After 5 attempts the row moves to `webhook_delivery_dead`. Non-2xx and exceptions both count as failures.

## Development

```bash
mvn verify                                  # full build + all tests (needs Docker)
mvn -pl api -am test                        # api unit tests only
mvn -pl worker -am verify                   # worker unit + sandbox ITs

# Faster local iteration: enable Testcontainers reuse
echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties
```

Tests live alongside source: surefire runs `*Test.java`, failsafe runs `*IT.java`. The worker docker ITs are tagged `@Tag("docker")` and need a working Docker daemon.

### Adding a language

1. Add `sandbox-images/<lang>/Dockerfile` (non-root UID 1000, GNU `time` installed).
2. Wire a build execution in `worker/pom.xml`.
3. Add a Flyway migration that inserts/updates the `language` row with `image`, `compile_command`, `run_command`, and `source_file`.
4. Add an entry to `MultiLanguageSandboxIT`.

## Module layout

```
api/                 Spring Boot REST + persistence + webhook drainer
worker/              Spring Boot daemon, Docker-in-Docker via mounted socket
common/              JobMessage / JobResult DTOs shared by api + worker
sandbox-images/      One Dockerfile per language
```

## Project status

Complete: language catalog, async submission, real sandbox execution with CPU/memory metrics, idempotency, webhooks with retry + dead-letter, multi-language WA comparison, full `docker compose` stack.

---

Built by Rishit
