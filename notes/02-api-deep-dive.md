# OCEE — API Service Deep Dive

## REST Endpoints

All routes prefixed `/api`. Errors use RFC 7807 `application/problem+json` format.

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/submissions` | Create and optionally wait for a submission |
| `GET`  | `/api/submissions/{token}` | Fetch result by UUID token |
| `GET`  | `/api/submissions` | Cursor-paginated list with filters |
| `GET`  | `/api/languages` | List all active languages |
| `GET`  | `/api/languages/{id}` | Single language |
| `GET`  | `/api/healthz` | Liveness probe |
| `GET`  | `/api/readyz` | Readiness probe |
| `GET`  | `/actuator/health` | Spring Boot health |
| `GET`  | `/actuator/prometheus` | Prometheus metrics |
| `GET`  | `/swagger-ui.html` | Interactive API docs |

---

## Submission Request Body

```json
{
  "language_id": 1,
  "source_code": "print(2+2)",
  "stdin": "",
  "expected_output": "4\n",
  "callback_url": "https://myserver.com/webhook",
  "cpu_time_limit": 2.0,
  "memory_limit": 128000
}
```

- `language_id` — required. Maps to a row in the `language` table.
- `source_code` — required. The code to execute.
- `stdin` — optional. Fed to the program's stdin.
- `expected_output` — optional. If set, enables **Wrong Answer** detection.
- `callback_url` — optional. If set, API POSTs the result to this URL on completion.
- `cpu_time_limit` — optional. Capped at language's `max_cpu_time`.
- `memory_limit` — optional. Capped at language's `max_memory`.

**Query params:**
- `?wait=true` — blocks until result ready (or timeout, max 10s)
- `?timeout=N` — how long to block (1–10s)

**Headers:**
- `Idempotency-Key: <UUID>` — safe retry header (details below)

---

## Status Codes Returned

```
Status code | Description
------------|----------------------------------
1           | In Queue
2           | Processing
3           | Accepted  (exit 0, output matched)
4           | Wrong Answer
5           | Time Limit Exceeded
6           | Compilation Error
7           | Runtime Error (SIGSEGV)
8           | Runtime Error (SIGXFSZ)
9           | Runtime Error (SIGFPE)
10          | Runtime Error (SIGABRT)
11          | Runtime Error (NZEC) — Non-Zero Exit Code
12          | Runtime Error (Other)
13          | Internal Error
14          | Exec Format Error
15          | Memory Limit Exceeded
```

---

## Submission Response Example

```json
{
  "token": "019e4693-828b-780e-8928-045e1c33ba2b",
  "status": { "code": 3, "description": "Accepted" },
  "language_id": 1,
  "source_code": "print(2+2)",
  "std_out": "4\n",
  "std_err": "",
  "exit_code": 0,
  "time": 0.05,
  "wall_time": 0.06,
  "memory": 8228,
  "execution_host": "worker-container-id",
  "cpu_time_limit": 2.0,
  "memory_limit": 128000,
  "created_at": "2026-05-21T12:00:00Z",
  "finished_at": "2026-05-21T12:00:01Z"
}
```

- `token` — UUID to poll for result later
- `time` — CPU time (user + sys seconds, from GNU time)
- `wall_time` — real elapsed time
- `memory` — peak RSS in KiB

---

## The `?wait=true` Mechanism (WaitRegistry)

When a client sends `?wait=true`, the API:

1. Creates the submission and inserts it into the database (status=QUEUED)
2. Calls `waitRegistry.register(token)` → gets a `CompletableFuture`
3. Blocks on `future.get(timeoutSeconds)` — up to 10 seconds
4. When worker publishes result, `ResultStreamConsumer` updates the DB and calls `waitRegistry.complete(token, response)`
5. The `CompletableFuture` resolves → client gets the response immediately

**Why not just poll?**
- Polling wastes requests (and adds latency).
- `CompletableFuture` + thread blocking is efficient — no busy loop.

**Edge case — result arrives before client even starts waiting:**
- The result might arrive in Redis and complete the future *before* `register()` is called.
- `WaitRegistry.complete()` uses `computeIfAbsent` — it creates and immediately completes the future.
- Then when `register()` is called, it finds the already-completed future and returns immediately.
- Parked (completed) futures are cleaned up after 30 seconds.

---

## Idempotency

**Purpose:** safe retries — if a client sends the same request twice (e.g. network retry), it gets back the same result without creating a duplicate submission.

**How it works:**
1. Client sends header `Idempotency-Key: <some-uuid>`
2. API computes SHA-256 hash of the raw request body
3. Looks up the key in the DB
4. If found and body hash matches → return existing submission
5. If found but body hash differs → return `409 Conflict`
6. If not found → create new submission, store key + hash

**Code location:** `IdempotencyChecker.java` + `SubmissionService.create()`

**Why hash the body?**
- Prevents the same key being accidentally reused with different code.
- Catches bugs where a client rotates keys but forgets to reset them.

---

## Cursor-Based Pagination

The `GET /api/submissions` list endpoint uses cursor pagination instead of offset pagination.

**Why not offset (`?page=2`)?**
- Offset pagination is slow on large tables (DB scans N rows to skip them).
- It also has a "shifting page" bug — if rows are inserted between pages, you see duplicates or miss rows.

**How cursor works:**
1. Client requests first page (no cursor param)
2. API returns `{ items: [...], next_cursor: "eyJ..." }`
3. Client sends next request with `?cursor=eyJ...`
4. API decodes cursor to get `(created_at, id)` of last seen row
5. Query uses `WHERE (created_at, id) < (cursor_created_at, cursor_id)` — stable, indexed

**Cursor encoding:** `CursorCodec` encodes `created_at|id` as Base64 URL-safe string.

**Index used:** `submission_cursor_idx ON submission (created_at DESC, id DESC)`

---

## JPA Entities

### Submission (main table)
```
id              BIGSERIAL PK
token           UUID UNIQUE — public identifier
language_id     FK to language
source_code     TEXT
std_in          TEXT
expected_output TEXT
status          INT (1-15, maps to Status enum)
std_out         TEXT
std_err         TEXT
compile_output  TEXT
exit_code       INT
exit_signal     INT
time            DOUBLE — CPU seconds
wall_time       DOUBLE — real seconds
memory          INT — peak KiB
execution_host  VARCHAR — which worker ran it
idempotency_key UUID — for idempotency check
idempotency_body_sha256  BYTEA — body hash
callback_url    VARCHAR
created_at      TIMESTAMPTZ
finished_at     TIMESTAMPTZ
```

### Language (reference table)
```
id              INT PK (1=Python, 2=C, 3=C++, 4=Java, 5=Node)
name            VARCHAR
version         VARCHAR
source_file     VARCHAR — e.g. "main.py", "Main.java"
compile_command TEXT — null for interpreted languages
run_command     TEXT — e.g. "python3 main.py"
image           VARCHAR — Docker image name e.g. "ocee/sandbox-python:3.11"
default_cpu_time DOUBLE
default_memory  INT
max_cpu_time    DOUBLE
max_memory      INT
max_source_size INT
compile_cpu_time DOUBLE
compile_memory  INT
is_active       BOOLEAN
```

### Outbox (transactional buffer)
```
id             BIGSERIAL PK
submission_id  BIGINT FK
payload        JSONB — the full JobMessage JSON
attempts       INT
next_attempt   TIMESTAMPTZ
created_at     TIMESTAMPTZ
```

---

## Resource Limits Resolution

`ResourceLimitsResolver` applies limits to a submission:
- If request specifies `cpu_time_limit`, use it — but cap at language's `max_cpu_time`
- If request doesn't specify, use language's `default_cpu_time`
- Same logic for memory
- This prevents a user from requesting 1000 seconds of CPU time

---

## Exception Handling

`GlobalExceptionHandler` catches all exceptions and returns RFC 7807 problem JSON:
```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Submission abc123 not found"
}
```

Custom exceptions:
- `SubmissionNotFoundException` → 404
- `LanguageNotFoundException` → 404
- `IdempotencyConflictException` → 409
- `ResourceLimitExceededException` → 422
- `InvalidCursorException` → 400
