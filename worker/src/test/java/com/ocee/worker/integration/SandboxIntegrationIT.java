package com.ocee.worker.integration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.common.Status;
import com.ocee.worker.sandbox.*;
import org.junit.jupiter.api.*;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
class SandboxIntegrationIT {

    static DockerClient docker;
    SandboxOrchestrator orch;
    VolumeManager vm;
    ContainerRunner cr;
    String workerId = "it-host";

    @BeforeAll static void up() {
        var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        docker = DockerClientImpl.getInstance(cfg,
                new ZerodepDockerHttpClient.Builder().dockerHost(cfg.getDockerHost()).build());
    }
    @AfterAll static void down() throws Exception { docker.close(); }

    @BeforeEach void wire() {
        vm = new VolumeManager(docker, workerId);
        cr = new ContainerRunner(docker, workerId, 65536);
        orch = new SandboxOrchestrator(vm, cr, workerId, 65536, 16_777_216L);
    }

    private JobMessage py(String src, String stdin) {
        return new JobMessage(UUID.randomUUID().toString(), 1, "python3 main.py", null,
                "main.py", src, stdin, null, 2.0, 0.5, 4.0, 65536,
                null, null, null, null, "ocee/sandbox-python:3.11", 10.0, 524288);
    }
    private JobMessage java(String src, String stdin) {
        return new JobMessage(UUID.randomUUID().toString(), 4, "java Main", "javac Main.java",
                "Main.java", src, stdin, null, 3.0, 0.5, 6.0, 262144,
                null, null, null, null, "ocee/sandbox-java:21", 10.0, 524288);
    }

    @Test void pythonAc() {
        JobResult r = orch.execute(py("print('hello')", ""), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.AC);
        assertThat(r.stdOut()).isEqualTo("hello\n");
    }
    @Test void pythonNzec() {
        JobResult r = orch.execute(py("import sys; sys.exit(2)", ""), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.NZEC);
    }
    @Test void pythonTle() {
        JobMessage m = py("while True: pass", "");
        m = new JobMessage(m.token(), m.languageId(), m.runCommand(), m.compileCommand(), m.sourceFile(),
                m.sourceCode(), m.stdIn(), m.expectedOutput(), 0.1, m.cpuExtraTimeLimit(), m.wallTimeLimit(),
                m.memoryLimit(), m.stackLimit(), m.maxProcessesAndOrThreadsLimit(), m.maxFileSizeLimit(),
                m.traceContext(), m.image(), m.compileCpuTime(), m.compileMemory());
        JobResult r = orch.execute(m, "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.TLE);
    }
    @Test void pythonMle() {
        var m = py("a = bytearray(64*1024*1024)\nfor i in range(0, len(a), 4096): a[i] = 1\nprint(len(a))\n", "");
        m = new JobMessage(m.token(), m.languageId(), m.runCommand(), m.compileCommand(), m.sourceFile(),
                m.sourceCode(), m.stdIn(), m.expectedOutput(), m.cpuTimeLimit(), m.cpuExtraTimeLimit(),
                m.wallTimeLimit(), 32768, m.stackLimit(), m.maxProcessesAndOrThreadsLimit(),
                m.maxFileSizeLimit(), m.traceContext(), m.image(), m.compileCpuTime(), m.compileMemory());
        JobResult r = orch.execute(m, "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.MLE);
    }
    @Test void javaAc() {
        var src = "public class Main { public static void main(String[] a){ System.out.println(\"hello\"); } }";
        JobResult r = orch.execute(java(src, ""), "ocee/sandbox-java:21", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.AC);
        assertThat(r.stdOut()).isEqualTo("hello\n");
    }
    @Test void javaCe() {
        var src = "public class Main { public static void main(String[] a){ System.out.println(\"x\") } }";
        JobResult r = orch.execute(java(src, ""), "ocee/sandbox-java:21", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.CE);
        assertThat(r.compileOutput()).contains("error");
    }
    @Test void outputTruncationFlag() {
        var src = "for _ in range(200000): print('x' * 100)";
        JobResult r = orch.execute(py(src, ""), "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(r.outputTruncated()).isTrue();
        assertThat(r.stdOut().length()).isLessThanOrEqualTo(65536);
    }
    @Test void cleanupAfterFailure() {
        var m = py("print('z')", "");
        orch.execute(m, "ocee/sandbox-python:3.11", 10.0, 524288);
        assertThat(docker.listVolumesCmd().exec().getVolumes())
                .noneMatch(v -> v.getName().contains(m.token()));
    }
}
