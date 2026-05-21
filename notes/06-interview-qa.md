# OCEE — Interview Questions & Answers

This file contains questions you are likely to be asked about this project in interviews. Read every answer and understand it. Do not memorize — understand.

---

## Section 1: Project Overview

**Q: What is OCEE? Explain it in one sentence.**
> OCEE is an online code execution engine that accepts source code via a REST API, runs it safely inside an isolated Docker container, and returns the output along with CPU time and memory metrics.

---

**Q: What problem does OCEE solve?**
> Running untrusted user code on a server is dangerous. Users could crash the server with fork bombs, read sensitive files, make network calls, or consume unlimited CPU. OCEE solves this by running each submission in a throwaway Docker container with strict resource and security limits, then destroying the container immediately after.

---

**Q: What languages are supported and how would you add a new one?**
> Currently: Python 3.11, C (GCC 13), C++ (GCC 13), Java 21, Node.js 20.
> To add a new language:
> 1. Create `sandbox-images/<lang>/Dockerfile` — must have non-root user (UID 1000) and GNU `time`
> 2. Add a Docker build execution in `worker/pom.xml`
> 3. Write a Flyway migration to insert a `language` row with `image`, `compile_command`, `run_command`, `source_file`
> 4. Add the language to `MultiLanguageSandboxIT` integration test

---

## Section 2: Architecture

**Q: Why are the API and Worker separate services?**
> Separation of concerns and independent scalability:
> - The API handles HTTP, validation, and persistence. It is I/O bound (waiting on DB and Redis).
> - The Worker handles code execution. It is CPU/resource bound (running Docker containers).
> - They communicate via Redis Streams — asynchronous and decoupled.
> - You can scale workers horizontally without touching the API.
> - A worker crash doesn't affect the API, and vice versa.

---

**Q: Walk me through what happens when I POST a submission.**
> 1. API validates the request body (language exists, source code within size limit, limits within bounds)
> 2. Checks idempotency key if provided
> 3. Saves a `submission` row (status=QUEUED) and an `outbox` row in a single DB transaction
> 4. Returns the submission token (202 or 201)
> 5. `OutboxDrainer` picks up the outbox row and publishes a `JobMessage` to Redis Stream `ocee.jobs`
> 6. Worker's `JobStreamConsumer` reads the message, calls `DockerSandboxExecutor`
> 7. Executor creates a Docker volume, runs container(s), captures output, destroys container/volume
> 8. Worker publishes `JobResult` to Redis Stream `ocee.results`
> 9. API's `ResultStreamConsumer` updates the submission row in Postgres
> 10. If `wait=true`, the blocked HTTP request is unblocked via `WaitRegistry`

---

**Q: Why not have the API publish directly to Redis instead of using an Outbox?**
> Because direct publish breaks atomicity. The API saves to DB and publishes to Redis — two separate systems. If the API crashes between the two operations, the submission exists in DB but no job ever gets queued, leaving it stuck as "In Queue" forever. The Outbox pattern wraps both writes in a single DB transaction. The drainer handles reliable delivery to Redis with retries.

---

**Q: How does the worker know how many jobs to run in parallel?**
> `SandboxProperties.effectiveConcurrency()` returns `min(availableProcessors, 4)` unless explicitly configured. A `Semaphore` limits in-flight jobs, and a `FixedThreadPool` of the same size runs them. This prevents a single worker from overloading the host Docker daemon.

---

## Section 3: Security & Sandboxing

**Q: How does OCEE prevent malicious code from harming the server?**
> Multiple layers of defense in Docker container configuration:
> - `--network none` — no network access
> - `--read-only` — filesystem is read-only
> - Only `/tmp` is writable (tmpfs, size-limited via `--tmpfs`)
> - `--memory` + `--memory-swap` set to same value — hard memory cap, no swap
> - `--cpus 1` — single CPU only
> - `--pids-limit 50` — prevents fork bombs
> - `--cap-drop ALL` — drops all Linux capabilities
> - `--security-opt no-new-privileges:true` — can't gain more privileges
> - `--user 1000:1000` — runs as non-root `runner` user
> - `--init` (tini) — proper PID 1 for signal handling

---

**Q: How does OCEE detect Time Limit Exceeded?**
> `ContainerRunner` waits for the container to finish using a `CountDownLatch` with a wall-clock timeout (based on `RunLimits.wallClock()`). If the container hasn't finished in time, it calls `docker kill` and returns `timedOut = true`. The `SandboxOrchestrator` maps this to `Status.TLE`.

---

**Q: How does OCEE detect Memory Limit Exceeded?**
> Docker's `--memory` and `--memory-swap` flags enforce a hard cap. When the container exceeds the limit, the kernel OOM-killer terminates it. After the container exits, `docker inspect` returns `OOMKilled: true`. The orchestrator maps this to `Status.MLE`.

---

**Q: How are CPU metrics collected?**
> GNU `time` is installed in every sandbox image. The actual command is wrapped:
> ```
> /usr/bin/time -f '__OCEE_METRICS__%M %U %S %e__OCEE_METRICS__' sh -c '<command>'
> ```
> GNU time writes metrics to stderr in the format `maxRssKib userCpuSec sysCpuSec wallSec`.
> `ContainerRunner` extracts the metrics block from stderr using the marker, then strips it so the user doesn't see it in their `std_err` output.

---

**Q: Why does the sandbox use a Docker volume instead of copying files directly into the container?**
> For compiled languages (C, C++, Java), two containers are used: one for compilation, one for execution. The compiled binary needs to be shared between them. A named Docker volume (mounted at `/work`) persists across both containers. For interpreted languages, it still works — source is copied into the volume before the single run container starts.

---

## Section 4: Reliability Patterns

**Q: What is the Outbox Pattern and why does OCEE use it?**
> The Outbox Pattern ensures reliable message delivery from a database-backed service to a messaging system (Redis here). Instead of publishing directly to Redis after a DB write (which could fail midway), the message is written to an `outbox` table in the same transaction as the main record. A background drainer reads the outbox and publishes to Redis, retrying on failure. This guarantees at-least-once delivery.

---

**Q: What happens if the worker crashes mid-execution?**
> Redis Streams keep messages in a "pending" state until acknowledged. If the worker crashes before acknowledging, the message stays pending. `PendingJobReclaimer` runs periodically, finds messages that have been pending for too long (worker presumed dead), and re-claims them for re-processing. The submission will be re-executed.

---

**Q: How does `?wait=true` work without polling the database?**
> The API registers a `CompletableFuture` in `WaitRegistry` keyed by the submission token. The HTTP thread blocks on `future.get(timeout)`. When the result arrives from Redis, `ResultStreamConsumer` calls `waitRegistry.complete(token, response)`, which completes the future. The HTTP thread unblocks and returns the response to the client immediately — no polling, no busy loop.

---

**Q: What is idempotency and why is it important here?**
> Idempotency means calling an operation multiple times produces the same result. For submissions, clients may retry due to network failures. Without idempotency, a retry creates a duplicate submission and double-bills the user's time/resources. With the `Idempotency-Key` header, the API recognizes retries and returns the original submission instead of creating a new one. The body hash prevents the same key from being accidentally used with different code.

---

**Q: How does webhook delivery handle failures?**
> `WebhookDrainer` attempts delivery every second. On failure (non-2xx or exception), it increments the attempt count and schedules a retry after a backoff period: 1s → 5s → 30s → 5m → 5m. After 5 failures, the delivery is moved to `webhook_delivery_dead` (dead letter queue). Developers can inspect the dead letter table to diagnose persistent webhook failures.

---

## Section 5: Database & Pagination

**Q: Why use cursor-based pagination instead of offset pagination?**
> Offset pagination (`LIMIT 50 OFFSET 100`) requires the DB to scan and discard 100 rows — gets slower as pages increase. It also has a "page drift" bug: if new rows are inserted between requests, you see duplicates or skip rows. Cursor pagination uses `WHERE (created_at, id) < (cursor_created_at, cursor_id)` with a composite index — always fast regardless of page number, and stable even if new data arrives.

---

**Q: Why does the submission table have both `id` (integer) and `token` (UUID)?**
> `id` is a sequential integer — cheap to join, used internally for cursor pagination. `token` is a random UUID exposed to clients — it's unguessable, so clients can't enumerate other users' submissions by incrementing an ID. This is standard practice for APIs that expose resource identifiers publicly.

---

**Q: What indexes does the database use and why?**
> - `submission_cursor_idx ON (created_at DESC, id DESC)` — supports cursor pagination
> - `submission_status_created_idx ON (status, created_at)` — supports filtered list queries
> - `submission_idempotency_uidx ON (idempotency_key) WHERE idempotency_key IS NOT NULL` — partial unique index, efficient for idempotency checks without impacting rows that don't have a key
> - `outbox_next_attempt_idx ON (next_attempt)` — OutboxDrainer poll query
> - `webhook_delivery_next_idx ON (next_attempt)` — WebhookDrainer poll query

---

**Q: Why does Flyway manage the schema instead of Hibernate auto-DDL?**
> Hibernate `create`/`update` DDL is unpredictable in production. Flyway gives explicit, versioned, irreversible migrations that are tracked in source control. You know exactly what SQL ran against your database and when. `ddl-auto: validate` ensures Hibernate's entity model matches the Flyway-managed schema at startup.

---

## Section 6: Technology Choices

**Q: Why Spring Boot?**
> Spring Boot provides auto-configuration for JPA, Redis, scheduling, REST, and validation out of the box. Spring Data JPA reduces boilerplate for repository code. Spring Scheduling handles outbox and webhook draining. It's battle-tested for production services.

**Q: Why Postgres?**
> ACID transactions are essential for the Outbox Pattern — both the submission row and outbox row must commit together or not at all. Postgres has excellent JSON support (JSONB) used for the outbox payload. It also has rich indexing options (partial indexes for idempotency).

**Q: Why Redis (Streams specifically)?**
> Lighter than Kafka, already in the stack, supports consumer groups (load balancing across workers), and provides message persistence (unlike Pub/Sub). For the scale this service targets, Redis Streams are the right trade-off between simplicity and reliability.

**Q: Why Docker-in-Docker (mounting the socket) instead of running code directly?**
> Running code in a Docker container gives hard resource isolation at the kernel level. Alternatives like `seccomp` sandboxes, `firejail`, or language-level sandboxes are harder to configure and less battle-tested. Docker provides isolation out of the box with a clean API. Socket mounting (`/var/run/docker.sock`) lets the worker service control Docker without being root itself.

---

## Section 7: How to Explain Your Contribution

If asked "what did you work on in this project?":

> "I studied and set up the OCEE project — an online code execution engine built with Spring Boot, PostgreSQL, Redis, and Docker. The system uses an outbox pattern for reliable job delivery to Redis Streams, consumer groups for scalable worker parallelism, and Docker containers with strict security hardening to safely run untrusted code in Python, C, C++, Java, and Node.js. I understand the full request lifecycle from API submission through sandbox execution to result delivery."

---

## Quick Reference Cheat Sheet

| Concept | Implementation |
|---------|---------------|
| Job queue | Redis Stream `ocee.jobs` |
| Result delivery | Redis Stream `ocee.results` |
| Reliable messaging | Outbox pattern (Postgres → drainer → Redis) |
| Sync wait | `CompletableFuture` in `WaitRegistry` |
| Safe retries | `Idempotency-Key` + SHA-256 body hash |
| Pagination | Cursor (Base64 encoded `created_at\|id`) |
| Sandbox isolation | Docker: `--network none`, `--read-only`, `--cap-drop ALL`, `--pids-limit 50` |
| CPU/memory metrics | GNU `time -f` in stderr, parsed with markers |
| Schema management | Flyway (8 migrations, V1–V8) |
| Webhook retries | 5 attempts, backoff: 1s → 5s → 30s → 5m → 5m |
| Dead letter | `webhook_delivery_dead` table after 5 failures |
| Worker crash recovery | Redis pending list + `PendingJobReclaimer` |
| Two-stage execution | Compile container (R/W volume) → Run container (R/O volume) |
| API port | 8080 (mapped to 8081 on Windows/Oracle conflict) |
| Swagger UI | http://localhost:8081/swagger-ui.html |
