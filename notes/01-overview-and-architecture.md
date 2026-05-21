# OCEE — Overview & Architecture

## What Is OCEE?

**OCEE (Online Code Execution Engine)** is a backend service that:
- Accepts source code over a REST API
- Runs it inside a secure, isolated Docker container (sandbox)
- Returns the result: `stdout`, `stderr`, `exit code`, CPU time, and memory usage

Think of it as the backend of an online judge (like LeetCode, Codeforces, or HackerRank) — you submit code, it runs it safely, and tells you if it passed.

**Supported languages:** Python 3.11, C (GCC 13), C++ (GCC 13), Java 21, Node.js 20

---

## Why Is This Hard?

Running untrusted user code is dangerous. Naïve execution lets users:
- Fork-bomb the server (crash it with infinite processes)
- Read/write the filesystem
- Make network calls
- Use unlimited CPU/memory

OCEE solves this with Docker isolation + strict resource limits.

---

## High-Level Architecture

```
                        ┌──────────────────────────────────┐
                        │             CLIENT               │
                        │  (curl / Swagger UI / app)       │
                        └─────────────┬────────────────────┘
                                      │ POST /api/submissions
                                      ▼
                        ┌─────────────────────────┐
                        │        API SERVICE       │
                        │  (Spring Boot, port 8080)│
                        │                          │
                        │  1. Validates request    │
                        │  2. Saves to Postgres    │
                        │  3. Writes to Outbox     │
                        │  4. Returns token        │
                        └──────┬──────────┬────────┘
                               │          │
                    Outbox     │          │ Reads results
                    Drainer    │          │ from ocee.results
                    publishes  │          │
                    to Redis   │          │
                               ▼          ▼
                        ┌────────────────────────────┐
                        │          REDIS             │
                        │                            │
                        │  ocee.jobs   (job queue)   │
                        │  ocee.results (results)    │
                        └──────────┬─────────────────┘
                                   │
                                   ▼
                        ┌─────────────────────────┐
                        │      WORKER SERVICE      │
                        │  (Spring Boot daemon)    │
                        │                          │
                        │  1. Reads job from Redis │
                        │  2. Creates Docker vol   │
                        │  3. Runs sandbox image   │
                        │  4. Captures output      │
                        │  5. Publishes result     │
                        └──────────┬───────────────┘
                                   │
                            docker run
                                   │
                                   ▼
                        ┌─────────────────────────┐
                        │   SANDBOX CONTAINER      │
                        │  (ocee/sandbox-python,   │
                        │   ocee/sandbox-java, ...) │
                        │                          │
                        │  - No network            │
                        │  - Read-only filesystem  │
                        │  - CPU/memory capped     │
                        │  - Non-root user (1000)  │
                        └─────────────────────────┘
```

---

## The Four Services (Docker Compose)

| Service    | Image                        | Port | Role |
|------------|------------------------------|------|------|
| `postgres` | postgres:16                  | 5432 | Stores submissions, languages, outbox, webhooks |
| `redis`    | redis:7                      | 6379 | Job queue + results stream (Redis Streams) |
| `api`      | Built from `api/Dockerfile`  | 8080 | REST API, persistence, webhook delivery |
| `worker`   | Built from `worker/Dockerfile` | —  | Job runner, sandbox orchestrator |

---

## Module Layout

```
ocee-main/
├── api/             Spring Boot REST + persistence + webhook drainer
│   └── src/main/java/com/ocee/
│       ├── controller/     REST endpoints
│       ├── service/        Business logic
│       ├── entity/         JPA entities (DB tables)
│       ├── repository/     Spring Data JPA repos
│       ├── queue/          Outbox drainer, Redis publisher/consumer
│       ├── webhook/        Webhook delivery + dead letter
│       ├── wait/           WaitRegistry (synchronous wait support)
│       ├── pagination/     Cursor-based pagination
│       ├── mapper/         Entity ↔ DTO conversions
│       └── exception/      Custom exceptions + global handler
│
├── worker/          Spring Boot daemon, no DB access
│   └── src/main/java/com/ocee/worker/
│       ├── consumer/       Redis Stream listener
│       ├── executor/       Executor interface + Docker implementation
│       ├── publisher/      Result publisher back to Redis
│       └── sandbox/        Container runner, metrics, volumes, reaper
│
├── common/          Shared DTOs (JobMessage, JobResult, Status enum)
│
└── sandbox-images/  One Dockerfile per language
    ├── python/
    ├── java/
    ├── c/
    ├── cpp/
    └── node/
```

---

## Key Design Decisions

### 1. API and Worker are separate services
- API handles HTTP and persistence only. It never runs code.
- Worker handles code execution only. It never touches the database.
- Communication is async via Redis Streams.
- Benefit: can scale each independently, worker can crash without affecting API.

### 2. Redis Streams (not Pub/Sub, not a queue library)
- Redis Streams are a persistent, ordered log (like Kafka but lighter).
- Consumer groups allow multiple workers to share load.
- Messages are acknowledged only after processing — no loss on crash.

### 3. Transactional Outbox Pattern
- API saves to DB + Outbox in one transaction.
- A background drainer reads the Outbox and publishes to Redis.
- Guarantees: if the API crashes between "save to DB" and "publish to Redis", the drainer will retry.
- Without this: crash = lost job.

### 4. Sandbox via Docker-in-Docker
- Worker mounts `/var/run/docker.sock` from host.
- Spawns fresh container per submission.
- Container is destroyed immediately after execution.
- No state leaks between submissions.

---

## Request Flow (Step by Step)

```
Client sends: POST /api/submissions?wait=true
  │
  ├─ API validates request body
  ├─ Checks idempotency key (if provided)
  ├─ Resolves language + resource limits
  ├─ Saves Submission row (status=QUEUED) to Postgres
  ├─ Saves Outbox row (JSON job message) to Postgres
  ├─ [TRANSACTION COMMITS]
  │
  ├─ OutboxDrainer (polling every 200ms) picks up outbox row
  ├─ Publishes JobMessage to Redis Stream ocee.jobs
  ├─ Deletes outbox row
  │
  ├─ Worker's JobStreamConsumer receives message from ocee.jobs
  ├─ Calls DockerSandboxExecutor.execute()
  │     ├─ Creates Docker volume for this submission
  │     ├─ Copies source code into volume
  │     ├─ [If compiled language] runs compile container
  │     ├─ Runs execution container with limits
  │     ├─ Parses metrics from stderr (GNU time output)
  │     └─ Removes container + volume
  │
  ├─ Worker publishes JobResult to Redis Stream ocee.results
  │
  ├─ API's ResultStreamConsumer receives JobResult
  ├─ Updates Submission row (status=AC/WA/TLE/etc.)
  ├─ If wait=true: completes CompletableFuture in WaitRegistry
  └─ Client receives final result
```
