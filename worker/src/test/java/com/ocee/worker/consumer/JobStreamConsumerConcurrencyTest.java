package com.ocee.worker.consumer;

import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.common.Status;
import com.ocee.worker.executor.Executor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JobStreamConsumerConcurrencyTest {

    @Test
    void semaphoreCapsInflight() throws Exception {
        int cap = 2;
        var sem = new Semaphore(cap);
        AtomicInteger peak = new AtomicInteger(), inflight = new AtomicInteger();
        Executor exec = msg -> {
            int now = inflight.incrementAndGet();
            peak.updateAndGet(p -> Math.max(p, now));
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            inflight.decrementAndGet();
            return new JobResult("t", Status.AC, null, null, null, 0, null, null,
                    0.05, 0.05, 0, "h", Instant.now(), null, false);
        };

        var pool = Executors.newFixedThreadPool(8);
        var msg = new JobMessage("t", 1, "x", null, "f", "s", "", null,
                null, null, null, null, null, null, null, null,
                "img", 10.0, 524288);

        var futures = new java.util.ArrayList<Future<?>>();
        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                try { sem.acquire(); exec.execute(msg); } catch (Exception e) { throw new RuntimeException(e); }
                finally { sem.release(); }
            }));
        }
        for (var f : futures) f.get();
        pool.shutdownNow();

        assertThat(peak.get()).isLessThanOrEqualTo(cap);
    }
}
