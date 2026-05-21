# OCEE — Database & Redis

## PostgreSQL Schema

### Table: `submission`
The central table. One row per code submission.

```sql
CREATE TABLE submission (
    id                BIGSERIAL PRIMARY KEY,          -- internal auto-increment ID
    token             UUID NOT NULL UNIQUE,           -- public-facing ID (given to clients)
    language_id       INT NOT NULL REFERENCES language(id),
    source_code       TEXT NOT NULL,
    std_in            TEXT,
    expected_output   TEXT,                           -- for Wrong Answer detection
    status            INT NOT NULL DEFAULT 1,         -- 1=Queued, 3=Accepted, etc.
    std_out           TEXT,
    std_err           TEXT,
    compile_output    TEXT,
    message           TEXT,
    exit_code         INT,
    exit_signal       INT,
    time              DOUBLE PRECISION,               -- CPU time (seconds)
    wall_time         DOUBLE PRECISION,               -- real elapsed time
    memory            INT,                            -- peak RSS in KiB
    execution_host    VARCHAR(128),                   -- which worker ran it
    cpu_time_limit    DOUBLE PRECISION,
    memory_limit      INT,
    idempotency_key   UUID,                           -- for safe retries
    idempotency_body_sha256 BYTEA,                   -- body hash for key validation
    callback_url      VARCHAR(2048),                  -- webhook URL
    created_at        TIMESTAMPTZ DEFAULT now(),
    finished_at       TIMESTAMPTZ,
    CONSTRAINT submission_status_range CHECK (status BETWEEN 1 AND 14)
);
```

**Why two IDs (id and token)?**
- `id` is a sequential integer — used internally for performance (joins, pagination cursors)
- `token` is a random UUID — exposed externally (clients can't guess other tokens, prevents enumeration attacks)

### Table: `language`
Reference table. Seeded by Flyway, rarely changes.

```sql
CREATE TABLE language (
    id               INT PRIMARY KEY,          -- 1=Python, 2=C, 3=C++, 4=Java, 5=Node
    name             VARCHAR(64) NOT NULL,
    version          VARCHAR(64),
    source_file      VARCHAR(255),             -- e.g. "main.py", "Main.java"
    compile_command  TEXT,                     -- NULL for interpreted languages
    run_command      TEXT NOT NULL,            -- e.g. "python3 main.py"
    image            VARCHAR NOT NULL,         -- Docker image: "ocee/sandbox-python:3.11"
    default_cpu_time DOUBLE PRECISION,
    default_memory   INT,
    max_cpu_time     DOUBLE PRECISION,
    max_memory       INT,
    max_source_size  INT,
    compile_cpu_time DOUBLE PRECISION,         -- separate limit for compile stage
    compile_memory   INT,
    is_active        BOOLEAN DEFAULT TRUE
);
```

### Table: `outbox`
Transactional buffer between API and Redis.

```sql
CREATE TABLE outbox (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submission(id),
    payload       JSONB NOT NULL,              -- full JobMessage as JSON
    created_at    TIMESTAMPTZ DEFAULT now(),
    attempts      INT NOT NULL DEFAULT 0,
    next_attempt  TIMESTAMPTZ DEFAULT now()   -- for exponential backoff
);
```

### Table: `webhook_delivery`
Pending webhook deliveries (active queue).

```sql
CREATE TABLE webhook_delivery (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL REFERENCES submission(id),
    url           VARCHAR(2048) NOT NULL,
    payload       JSONB NOT NULL,              -- full submission JSON to POST
    attempts      INT NOT NULL DEFAULT 0,
    next_attempt  TIMESTAMPTZ DEFAULT now(),
    last_error    TEXT,
    last_status   INT,
    created_at    TIMESTAMPTZ DEFAULT now()
);
```

### Table: `webhook_delivery_dead`
Submissions that failed all 5 webhook delivery attempts.

```sql
CREATE TABLE webhook_delivery_dead (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT NOT NULL,
    url           VARCHAR(2048) NOT NULL,
    payload       JSONB NOT NULL,
    attempts      INT NOT NULL,
    last_error    TEXT,
    last_status   INT,
    created_at    TIMESTAMPTZ DEFAULT now()
);
```

---

## PostgreSQL Indexes

```sql
-- For cursor-based pagination (list submissions newest first)
CREATE INDEX submission_cursor_idx ON submission (created_at DESC, id DESC);

-- For filtering by status + created_at in list queries
CREATE INDEX submission_status_created_idx ON submission (status, created_at);

-- For filtering by language in list queries
CREATE INDEX submission_language_idx ON submission (language_id);

-- Enforces idempotency uniqueness (partial: only when key is not null)
CREATE UNIQUE INDEX submission_idempotency_uidx
    ON submission (idempotency_key) WHERE idempotency_key IS NOT NULL;

-- For outbox drainer polling (next due row)
CREATE INDEX outbox_next_attempt_idx ON outbox (next_attempt);

-- For webhook drainer polling
CREATE INDEX webhook_delivery_next_idx ON webhook_delivery (next_attempt);
```

**Why partial index for idempotency?**
- Most submissions don't have an idempotency key
- A full unique index on `idempotency_key` would fail for multiple NULL rows
- Partial index (`WHERE idempotency_key IS NOT NULL`) only enforces uniqueness for non-null values
- Also smaller and faster than a full index

---

## JPA / Hibernate Configuration

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate   # Hibernate checks schema matches entities, NEVER creates/alters
    open-in-view: false    # Disables the OSIV anti-pattern
    properties:
      hibernate.jdbc.time_zone: UTC
```

**Why `ddl-auto: validate`?**
- Flyway manages schema changes, not Hibernate
- `validate` ensures the JPA entities match what Flyway created
- Prevents Hibernate from accidentally modifying the schema

**Why `open-in-view: false`?**
- OSIV (Open Session In View) keeps a DB transaction open for the entire HTTP request
- Causes lazy-loading queries to silently fire in the view layer
- Disabling it forces all DB access into the service layer (cleaner, more predictable)

---

## Redis Architecture

### Redis Streams vs other options

| Feature | Redis Pub/Sub | Redis List | Redis Stream |
|---------|---------------|------------|--------------|
| Message persistence | No | Yes | Yes |
| Consumer groups | No | No | Yes |
| Message replay | No | No | Yes |
| Delivery guarantee | At-most-once | At-least-once | At-least-once |
| Ordering | Yes | Yes | Yes |

OCEE uses Streams because it needs persistence (jobs survive Redis restart) and consumer groups (scale workers horizontally).

### Stream: `ocee.jobs`

**Producer:** `OutboxDrainer` (runs in API service)
```
Message structure:
{
  "payload": "<JSON of JobMessage>"
}
```

**Consumer group:** `ocee-workers`
**Consumers:** one per worker instance (`worker-<hostname>-<pid>`)

### Stream: `ocee.results`

**Producer:** `ResultStreamPublisher` (runs in Worker service)

**Consumer group:** `ocee-api`
**Consumer:** `ResultStreamConsumer` in the API service

### JobMessage (API → Worker)

```json
{
  "token": "019e4693-...",
  "language_id": 1,
  "run_command": "python3 main.py",
  "compile_command": null,
  "source_file": "main.py",
  "source_code": "print(2+2)",
  "std_in": null,
  "expected_output": null,
  "cpu_time_limit": 2.0,
  "memory_limit": 128000,
  "image": "ocee/sandbox-python:3.11",
  "compile_cpu_time": 5.0,
  "compile_memory": 256000
}
```

### JobResult (Worker → API)

```json
{
  "token": "019e4693-...",
  "status": "AC",
  "std_out": "4\n",
  "std_err": "",
  "compile_output": null,
  "exit_code": 0,
  "exit_signal": null,
  "cpu_time": 0.05,
  "wall_time": 0.06,
  "memory": 8228,
  "execution_host": "abc123",
  "finished_at": "2026-05-21T12:00:01Z"
}
```

---

## Spring Data Redis Integration

```java
// Configured in RedisStreamsConfig.java (API) and WorkerRedisStreamsConfig.java (Worker)
StreamMessageListenerContainer<String, MapRecord<String, String, String>> container
```

- `StringRedisTemplate` for basic Redis operations
- `StreamMessageListenerContainer` for async stream consumption
- Consumer runs in a background thread pool

### Pending Job Reclaimer
```java
// Runs periodically
redis.opsForStream().pending(jobsStream, consumerGroup, Range.unbounded(), count)
// Finds messages pending > N minutes (worker died before ack)
// Re-claims them: assigns to current worker
// Re-processes them
```

This prevents stuck jobs when a worker crashes.

---

## Data Flow Summary

```
[Client]
    │ POST /api/submissions
    ▼
[API: SubmissionController]
    │
    ▼
[API: SubmissionService.create()]
    │ @Transactional
    ├── INSERT INTO submission (status=QUEUED)
    └── INSERT INTO outbox (payload=JobMessage)
    │ [DB COMMIT]
    │
    ▼
[API: OutboxDrainer] (background, every 200ms)
    ├── SELECT FROM outbox WHERE next_attempt <= now()
    ├── XADD ocee.jobs {payload: JobMessage}
    └── DELETE FROM outbox
    │
    ▼
[Redis Stream: ocee.jobs]
    │
    ▼
[Worker: JobStreamConsumer]
    │ XREADGROUP GROUP ocee-workers CONSUMER worker-abc-123
    │
    ▼
[Worker: DockerSandboxExecutor.execute()]
    ├── docker volume create ocee-<token>
    ├── docker run <compile>  (if needed)
    ├── docker run <execute>
    └── docker volume rm ocee-<token>
    │
    ▼
[Worker: ResultStreamPublisher]
    └── XADD ocee.results {payload: JobResult}
    │
    ▼
[Redis Stream: ocee.results]
    │
    ▼
[API: ResultStreamConsumer]
    ├── UPDATE submission SET status=AC, std_out=..., time=..., finished_at=...
    ├── WebhookEnqueuer.enqueue() → INSERT INTO webhook_delivery
    └── WaitRegistry.complete(token, response)
    │
    ▼
[Client gets response] (either via wait=true or polling GET /api/submissions/{token})
```
