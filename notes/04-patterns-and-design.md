# OCEE — Design Patterns & Engineering Decisions

## 1. Transactional Outbox Pattern

### The Problem
When creating a submission, the API needs to:
1. Save the submission to Postgres (persistent)
2. Publish a job message to Redis (messaging)

These are two different systems. What if the API crashes between step 1 and step 2?

**Without outbox:** submission is saved in DB (status=QUEUED), but job is never published → submission stuck forever.

### The Solution: Outbox Table
```
POST /api/submissions
    │
    └─ SINGLE TRANSACTION:
        ├─ INSERT INTO submission (status=QUEUED)
        └─ INSERT INTO outbox (payload = JobMessage JSON)
    [COMMIT]

Background OutboxDrainer (every 200ms):
    ├─ SELECT * FROM outbox WHERE next_attempt <= now() LIMIT 50
    ├─ For each row:
    │   ├─ Publish to Redis Stream
    │   └─ DELETE from outbox (only if publish succeeds)
    └─ On failure: exponential backoff, max 5 attempts
```

**Key insight:** DB write and outbox write are in the **same transaction**. Either both succeed or both fail. The drainer then reliably forwards messages to Redis with retry.

### Backoff on failure
```java
private long backoffSeconds(int attempts) {
    return Math.min(60L, 1L << Math.min(attempts, 6));
}
// attempt 1: 2s, 2: 4s, 3: 8s, 4: 16s, 5: 32s, 6: 60s (max)
```

---

## 2. Redis Streams (not Kafka, not RabbitMQ)

### What are Redis Streams?
- An append-only log of messages (like Kafka topics)
- Support **consumer groups**: multiple consumers share the load
- Each message is processed by exactly one consumer in the group
- Messages stay "pending" until explicitly acknowledged

### Why Redis Streams over Pub/Sub?
- Pub/Sub: if consumer is offline when message published, message is **lost**
- Streams: messages are persisted, consumer can catch up after restart

### Why Redis over Kafka?
- Simpler to deploy (no ZooKeeper, no cluster setup)
- Already using Redis → one less service
- Traffic volume here doesn't justify Kafka overhead

### Stream names
- `ocee.jobs` — API → Worker (job submissions)
- `ocee.results` — Worker → API (execution results)
- `ocee.jobs.dead` — dead letter (for failed messages that exceeded retries)

### Consumer group
```java
// Worker subscribes as:
group = "ocee-workers"
consumer = "worker-<hostname>-<pid>"

// API subscribes to results as:
group = "ocee-api"
```

Multiple worker pods can subscribe to the same group — Redis distributes messages across them automatically (horizontal scaling!).

---

## 3. Synchronous Wait (WaitRegistry)

### The Problem
REST clients often want synchronous behavior: "submit code, wait for result, return it."
But execution is async (goes through Redis and Docker).

### The Solution: CompletableFuture registry

```java
// When client sends ?wait=true:
CompletableFuture<SubmissionResponse> future = waitRegistry.register(token);
return future.get(timeout, SECONDS);  // blocks the HTTP thread

// When worker result arrives in ResultStreamConsumer:
waitRegistry.complete(token, response);  // unblocks the future
```

`WaitRegistry` is a `ConcurrentHashMap<UUID, CompletableFuture<SubmissionResponse>>`.

### Race condition handling
What if the result arrives before the client even calls `register()`?

```java
// complete() uses computeIfAbsent → creates + completes the future
public void complete(UUID token, SubmissionResponse response) {
    CompletableFuture<SubmissionResponse> f = waiters.computeIfAbsent(token, k -> new CompletableFuture<>());
    f.complete(response);
    cleanup.schedule(() -> waiters.remove(token, f), 30, SECONDS);
}

// register() also uses computeIfAbsent → finds the already-completed future
public CompletableFuture<SubmissionResponse> register(UUID token) {
    return waiters.computeIfAbsent(token, k -> new CompletableFuture<>());
}
```

Both `complete()` and `register()` use `computeIfAbsent` atomically → the first caller creates the future, second caller gets the same one. No race condition.

---

## 4. Idempotency

### The Problem
Network failures cause clients to retry. A retry should not create a duplicate submission.

### The Solution
```
Header: Idempotency-Key: <client-chosen UUID>

API logic:
1. Compute SHA-256 of request body
2. Look up key in submission table
3. If found + hash matches → return existing (same result, no new row)
4. If found + hash differs → 409 Conflict (same key, different body = error)
5. If not found → create new, store key + hash
```

### Why hash the body?
- Protects against accidental key reuse with different code
- If you reuse a key with identical body (pure retry) → fine
- If you reuse a key with different body → 409 signals a bug

### DB constraint
```sql
CREATE UNIQUE INDEX submission_idempotency_uidx
    ON submission (idempotency_key) WHERE idempotency_key IS NOT NULL;
```
Partial unique index — only applies when key is not null (submissions without idempotency key are unaffected).

---

## 5. Webhooks with Dead Letter Queue

### How it works
When a submission has `callback_url`:

1. `WebhookEnqueuer` (called by `ResultApplier`) inserts a `webhook_delivery` row
2. `WebhookDrainer` (scheduled every 1s) picks up due deliveries
3. POSTs the submission JSON to `callback_url`
4. On success (2xx) → deletes row
5. On failure → retry with backoff

### Retry schedule
```
Attempt 1: immediate
Attempt 2: wait 1s
Attempt 3: wait 5s
Attempt 4: wait 30s
Attempt 5: wait 5m
After 5 failures → move to webhook_delivery_dead table (dead letter)
```

### Headers sent to callback URL
```
Content-Type: application/json
User-Agent: OCEE-Webhook/1.0
X-OCEE-Submission-Token: <token>
X-OCEE-Delivery-Attempt: <1..5>
```

### Dead Letter
Failed deliveries after 5 attempts go to `webhook_delivery_dead`. Useful for debugging — you can inspect which webhooks failed and why.

---

## 6. Docker Sandbox Security

### Threat: user submits malicious code

**Attack → Defense:**

| Attack | Defense |
|--------|---------|
| Fork bomb (infinite processes) | `--pids-limit 50` |
| Read host filesystem | `--read-only` (rootfs is read-only) |
| Write to disk | Only `/tmp` is writable (tmpfs, size-limited) |
| Network exfiltration | `--network none` |
| Privilege escalation | `--cap-drop ALL` + `no-new-privileges:true` |
| Run as root | `--user 1000:1000` (non-root runner user) |
| Infinite CPU loop | `--cpus 1` + GNU time wall clock enforcement |
| OOM | `--memory` + `--memory-swap` (same value = no swap) |
| Leave files behind | Container + volume deleted after each run |
| Signal spoofing | `--init` (tini handles signals properly) |

---

## 7. Two-Stage Execution (Compile + Run)

For compiled languages:

### Stage 1: Compile container
- Mounts volume read-write
- Copies source file into volume
- Runs compiler command (e.g. `javac Main.java`)
- If exit != 0 → return `Compilation Error` immediately
- Compiled binary stays on the volume

### Stage 2: Run container
- Mounts same volume read-only (compiled binary is there)
- Runs the binary with resource limits
- Captures stdout/stderr/metrics

**Why separate containers?**
- Compile limits are different (more time/memory allowed for Java compilation)
- Security: run container is completely isolated from compile environment
- Clean separation of concerns

---

## 8. Flyway Database Migrations

Schema is managed by Flyway (versioned SQL migrations):

| Migration | What it adds |
|-----------|-------------|
| V1 | `language`, `submission`, `outbox` tables |
| V2 | Seeds initial language rows |
| V3 | Adds `image`, `compile_command` columns to `language` |
| V4 | Seeds sandbox image references for all 5 languages |
| V5 | Adds MLE (Memory Limit Exceeded) status |
| V6 | Activates all 5 sandbox languages |
| V7 | Adds `idempotency_body_sha256` column |
| V8 | Adds `callback_url` to submission, creates `webhook_delivery` and `webhook_delivery_dead` tables |

**Why Flyway?**
- Schema changes are tracked in code (version-controlled)
- Applied automatically on startup in order
- Can never be applied twice (checksum protection)
- Easy to audit what changed and when

---

## 9. Output Comparison (Wrong Answer Detection)

If `expected_output` is provided:

```java
// OutputComparator
if (submission.getExpectedOutput() != null && status == Status.AC) {
    if (!normalize(actual).equals(normalize(expected))) {
        return Status.WA; // Wrong Answer
    }
}
```

Normalization typically trims trailing whitespace and normalizes newlines — because some judges accept minor whitespace differences.

---

## 10. Jackson / JSON Configuration

- `SNAKE_CASE` property naming strategy → Java `sourceCode` field serializes as `source_code` in JSON
- `NON_NULL` inclusion → null fields are omitted from responses (clean output)
- Custom `ObjectMapper` bean named `streamObjectMapper` is used for Redis stream messages (avoids conflicts with HTTP request/response mapper)
