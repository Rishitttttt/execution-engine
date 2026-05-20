package com.ocee.worker.sandbox;

import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.common.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SandboxOrchestratorTest {

    private final VolumeManager vm = mock(VolumeManager.class);
    private final ContainerRunner runner = mock(ContainerRunner.class);
    private final SandboxOrchestrator orch = new SandboxOrchestrator(vm, runner, "host", 65_536, 67_108_864L);

    private JobMessage py(String src) {
        return new JobMessage("tok", 1, "python3 main.py", null, "main.py", src, "",
                null, 2.0, 0.5, 4.0, 65536, null, null, null, null,
                "ocee/sandbox-python:3.11", 10.0, 524288);
    }
    private JobMessage java21(String src, String compile) {
        return new JobMessage("tok", 4, "java Main", compile, "Main.java", src, "",
                null, 2.0, 0.5, 4.0, 262144, null, null, null, null,
                "ocee/sandbox-java:21", 10.0, 524288);
    }

    @Test
    void interpretedAcMapsToAc() {
        when(runner.runOnce(any(), any(), any(), eq(false), any(), any(), any(), eq("tok"), eq("run")))
                .thenReturn(new RunOutcome(0, null, false, false, false, "ok\n", "", false, 0.05, 1024L, null, null, null, null));
        JobResult r = orch.execute(py("print('ok')"), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.AC);
        assertThat(r.stdOut()).isEqualTo("ok\n");
    }

    @Test
    void compiledCompileFailureMapsToCe() {
        when(runner.runOnce(any(), any(), any(), eq(false), any(), any(), any(), eq("tok"), eq("compile")))
                .thenReturn(new RunOutcome(1, null, false, false, false, "", "Main.java:1: error\n", false, 0.1, null, null, null, null, null));
        JobResult r = orch.execute(java21("bad", "javac Main.java"), "ocee/sandbox-java:21", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.CE);
        assertThat(r.compileOutput()).contains("Main.java:1: error");
        verify(runner, never()).runOnce(any(), any(), any(), eq(true), any(), any(), any(), any(), eq("run"));
    }

    @Test
    void wallClockTimeoutMapsToTle() {
        when(runner.runOnce(any(), any(), any(), anyBoolean(), any(), any(), any(), eq("tok"), eq("run")))
                .thenReturn(new RunOutcome(-1, null, false, true, false, "", "", false, 0.5, null, null, null, null, null));
        JobResult r = orch.execute(py("..."), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.TLE);
    }

    @Test
    void oomMapsToMle() {
        when(runner.runOnce(any(), any(), any(), anyBoolean(), any(), any(), any(), eq("tok"), eq("run")))
                .thenReturn(new RunOutcome(137, null, true, false, false, "", "", false, 0.2, null, null, null, null, null));
        JobResult r = orch.execute(py("..."), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.MLE);
    }

    @Test
    void signalledMapsBySignal() {
        when(runner.runOnce(any(), any(), any(), anyBoolean(), any(), any(), any(), eq("tok"), eq("run")))
                .thenReturn(new RunOutcome(139, 11, false, false, false, "", "", false, 0.1, null, null, null, null, null));
        JobResult r = orch.execute(py("..."), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.SIGSEGV);
    }

    @Test
    void nonZeroNonSignalMapsToNzec() {
        when(runner.runOnce(any(), any(), any(), anyBoolean(), any(), any(), any(), eq("tok"), eq("run")))
                .thenReturn(new RunOutcome(2, null, false, false, false, "", "", false, 0.1, null, null, null, null, null));
        JobResult r = orch.execute(py("..."), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.NZEC);
    }

    @Test
    void dockerErrorMapsToBoxerr() {
        when(runner.runOnce(any(), any(), any(), anyBoolean(), any(), any(), any(), eq("tok"), eq("run")))
                .thenReturn(new RunOutcome(-1, null, false, false, true, "", "", false, 0.0, null, "boom", null, null, null));
        JobResult r = orch.execute(py("..."), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.BOXERR);
    }

    @Test
    void volumeAlwaysRemovedEvenOnRunnerException() {
        when(runner.runOnce(any(), any(), any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("kaboom"));
        try { orch.execute(py("..."), "ocee/sandbox-python:3.11", 10.0, 524288); } catch (Exception ignored) {}
        verify(vm).remove("tok");
    }

    @Test
    void truncationFlagPropagated() {
        when(runner.runOnce(any(), any(), any(), anyBoolean(), any(), any(), any(), eq("tok"), eq("run")))
                .thenReturn(new RunOutcome(0, null, false, false, false, "x", "", true, 0.05, null, null, null, null, null));
        JobResult r = orch.execute(py("..."), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.outputTruncated()).isTrue();
    }
}
