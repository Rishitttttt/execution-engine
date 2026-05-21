# OCEE — Worker Service & Sandbox Execution

## What the Worker Does

The worker is a separate Spring Boot application (no database access) that:
1. Reads jobs from Redis Stream `ocee.jobs`
2. Executes code in a Docker sandbox container
3. Publishes the result to Redis Stream `ocee.results`

It never talks to Postgres directly — all persistence is handled by the API.

---

## Worker Startup Flow

1. `WorkerApplication` starts (Spring Boot)
2. `DockerClientConfig` creates a `DockerClient` connected to `/var/run/docker.sock`
3. `ImageVerifier` checks that all sandbox images (ocee/sandbox-python:3.11, etc.) exist locally
4. `SandboxReaper` runs on startup to clean up any orphaned containers from a previous crash
5. `JobStreamConsumer` subscribes to the Redis Stream consumer group `ocee-workers`

---

## Concurrency Model

```java
// SandboxProperties
int concurrency = sandbox.effectiveConcurrency();
// = min(availableProcessors, 4) unless explicitly configured

// JobStreamConsumer
Semaphore inflight = new Semaphore(concurrency);
ExecutorService workerPool = Executors.newFixedThreadPool(concurrency);
```

- Fixed thread pool — at most N jobs run simultaneously
- Semaphore prevents more jobs from being pulled off the stream than threads can handle
- Each submission gets its own thread for the duration of execution

---

## Job Processing (JobStreamConsumer)

```
Redis Stream message arrives
    │
    ├─ acquire semaphore (blocks if N jobs already running)
    ├─ submit to thread pool
    │     ├─ deserialize payload → JobMessage
    │     ├─ executor.execute(msg) → JobResult
    │     ├─ resultPublisher.publish(result) → Redis ocee.results
    │     └─ redis.opsForStream().acknowledge(group, record) ← IMPORTANT
    └─ release semaphore
```

**Why acknowledge matters:**
Redis Streams in consumer group mode keep messages in a "pending" list until acknowledged.
If the worker crashes mid-execution, the message stays pending.
`PendingJobReclaimer` runs periodically to reclaim old pending messages from dead consumers.

---

## Executor Interface

```java
public interface Executor {
    JobResult execute(JobMessage msg);
}
```

Two implementations:
- `DockerSandboxExecutor` — real Docker execution (production)
- `MockExecutor` — returns fake Accepted result (tests without Docker)

Spring profile `docker` activates `DockerSandboxExecutor`.

---

## SandboxOrchestrator — The Core Execution Logic

For each job, `SandboxOrchestrator.execute()`:

### Step 1: Create Docker Volume
```java
vm.create(msg.token());
// creates a named volume: ocee-<token>
// volume is mounted into container at /work
```

### Step 2: Compile (if needed)

**Compiled languages (C, C++, Java):**
```
RunLimits compileLimits = RunLimits.compile(compileCpuSec, compileMemKib, tmpfsBytes);
RunOutcome co = runner.runOnce(image, compileCommand, volume, ...);
if co.exitCode() != 0 → return Status.CE (Compilation Error)
```

**Interpreted languages (Python, Node):**
- No compile step — `compileCommand` is null
- Skip directly to execution

### Step 3: Execute

```java
RunLimits runLimits = RunLimits.run(cpuSec, memKib, tmpfsBytes);
RunOutcome ro = runner.runOnce(image, runCommand, volume, readOnly=true, ...);
```

After compile: volume contains compiled binary → mount read-only
No compile: volume empty → copy source into it, mount read-write

### Step 4: Map outcome to Status

```
o.dockerError()  → BOXERR (Internal Error)
o.timedOut()     → TLE
o.oomKilled()    → MLE
o.signal() != null → SIGSEGV / SIGFPE / SIGABRT / etc.
o.exitCode() == 0 → AC (Accepted)
else              → NZEC (Non-Zero Exit Code)
```

### Step 5: Cleanup
```java
finally { vm.remove(msg.token()); }
// always removes volume even if exception thrown
```

---

## ContainerRunner — Docker API Details

`ContainerRunner.runOnce()` does the low-level Docker work:

### Container Configuration (security hardening)
```java
HostConfig hc = HostConfig.newHostConfig()
    .withNetworkMode("none")           // NO network access
    .withReadonlyRootfs(true)          // filesystem is read-only
    .withTmpFs(Map.of("/tmp", "rw,nosuid,nodev,size=..."))  // limited writable /tmp
    .withMemory(memoryKib * 1024L)     // hard memory cap
    .withMemorySwap(memoryKib * 1024L) // no swap (same as memory = no swap)
    .withCpuCount(1L)                  // single CPU
    .withPidsLimit(50L)                // max 50 processes (prevents fork bomb)
    .withCapDrop(Capability.ALL)       // drop ALL Linux capabilities
    .withSecurityOpts(["no-new-privileges:true"]) // can't gain privileges
    .withInit(true)                    // PID 1 is tini (proper signal handling)
    ...
```

### User
```java
.withUser("1000:1000")
// runs as non-root user "runner" (UID/GID 1000)
// same in all sandbox images
```

### Command wrapping with GNU time
```
/usr/bin/time -f '__OCEE_METRICS__%M %U %S %e__OCEE_METRICS__' sh -c '<actual command>'
```
- `%M` = max RSS in KiB
- `%U` = user CPU seconds
- `%S` = system CPU seconds
- `%e` = wall clock seconds
- These metrics appear in **stderr**, wrapped in markers so they can be extracted and stripped

### Execution
1. Copy source files into container via `docker cp` (as a tar archive)
2. `docker start <id>`
3. If stdin provided, attach stdin stream
4. Stream logs (stdout+stderr) via `docker logs --follow`
5. Wait for container to finish (wall clock timeout)
6. If not finished → `docker kill`
7. `docker inspect` → get exit code, OOM flag
8. Parse metrics from stderr
9. `docker rm --force` the container

---

## Metrics Parsing

GNU time writes to stderr in format:
```
__OCEE_METRICS__8228 0.03 0.01 0.06__OCEE_METRICS__
```
Fields: `maxRssKib userCpuSec sysCpuSec wallSec`

`MetricsParser` extracts these numbers.
`ContainerRunner` strips the metrics marker line from stderr before returning it to the client (user should not see internal markers).

---

## Sandbox Docker Images

Each language has a minimal Docker image in `sandbox-images/<lang>/Dockerfile`:

### Common properties (all images):
- Non-root user `runner` (UID/GID 1000)
- GNU `time` installed (`/usr/bin/time`) for metrics
- Working directory `/work`
- No extra packages

### Per language:
| Image | Base | Compile Command | Run Command |
|-------|------|----------------|-------------|
| `ocee/sandbox-python:3.11` | `python:3.11-slim-bookworm` | — | `python3 main.py` |
| `ocee/sandbox-java:21` | `eclipse-temurin:21-jdk-alpine` | `javac Main.java` | `java Main` |
| `ocee/sandbox-c:13` | `gcc:13-bookworm` | `gcc -o main main.c` | `./main` |
| `ocee/sandbox-cpp:13` | `gcc:13-bookworm` | `g++ -o main main.cpp` | `./main` |
| `ocee/sandbox-node:20` | `node:20-bookworm-slim` | — | `node main.js` |

---

## SandboxReaper

On startup and periodically, the reaper:
1. Lists all running Docker containers with label `ocee.worker-id = <this-worker>`
2. Kills and removes any that are older than `reaperMaxAge` (default 30s)
3. This handles cases where the worker crashed mid-execution and left orphaned containers

---

## VolumeManager

Creates and deletes named Docker volumes per submission:
- Name pattern: `ocee-<submission-token>`
- Volume is mounted at `/work` inside the container
- Allows compile step and run step to share files (compiled binary)
- Cleaned up in `finally` block regardless of outcome

---

## PendingJobReclaimer

Runs periodically to reclaim "stuck" pending messages in the Redis Stream:
- If a worker pulled a job but died before acknowledging it, the message sits in the pending list
- After a timeout, `PendingJobReclaimer` claims the message and re-processes it
- Prevents jobs from being stuck forever due to worker crashes

---

## Result Publishing

After execution, worker publishes to `ocee.results`:
```java
resultPublisher.publish(result);
// Adds JobResult JSON as a message to the Redis Stream ocee.results
```

API's `ResultStreamConsumer` (running in the API service):
1. Reads from `ocee.results`
2. Looks up the Submission by token
3. Updates: status, stdout, stderr, metrics, finishedAt
4. If `expectedOutput` was set, calls `OutputComparator` → may change AC to WA
5. If `callbackUrl` set, enqueues a `WebhookDelivery`
6. Calls `waitRegistry.complete(token, response)` to unblock any waiting HTTP clients
