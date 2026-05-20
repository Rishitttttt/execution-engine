package com.ocee.worker.executor;

import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.worker.config.WorkerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockExecutorTest {

    @Test
    void produceWellFormedResult() {
        WorkerProperties props = new WorkerProperties(30, 10000, 3, 5, 10);
        MockExecutor exec = new MockExecutor(props);
        JobMessage msg = new JobMessage("11111111-2222-7333-8444-555555555555",
                1, "python3 main.py", null, "main.py", "print(1)", null, null,
                2.0, null, null, 128000, null, null, null, "trace",
                "ocee/sandbox-python:3.11", 10.0, 524288);
        JobResult r = exec.execute(msg);
        assertThat(r.token()).isEqualTo(msg.token());
        assertThat(r.status()).isNotNull();
        assertThat(r.finishedAt()).isNotNull();
        assertThat(r.executionHost()).isNotBlank();
        assertThat(r.traceContext()).isEqualTo("trace");
    }
}
