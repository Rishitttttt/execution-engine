package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
class ContainerRunnerIT {
    static DockerClient docker;
    ContainerRunner runner;
    VolumeManager vm;
    String token;
    String volume;

    @BeforeAll static void up() {
        var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        docker = DockerClientImpl.getInstance(cfg,
                new ZerodepDockerHttpClient.Builder().dockerHost(cfg.getDockerHost()).build());
    }
    @AfterAll static void down() throws Exception { docker.close(); }

    @BeforeEach void each() {
        runner = new ContainerRunner(docker, "test-host", 65536);
        vm = new VolumeManager(docker, "test-host");
        token = UUID.randomUUID().toString();
        volume = vm.create(token);
    }
    @AfterEach void cleanup() { vm.remove(token); }

    @Test
    void runsToCompletionAndCapturesStdout() {
        var limits = new RunLimits(Duration.ofSeconds(5), 65536, 16_777_216, 64, 64);
        var out = runner.runOnce("ocee/sandbox-python:3.11", "echo hello",
                volume, true, null, null, limits, token, "run");
        assertThat(out.exitCode()).isZero();
        assertThat(out.stdout()).isEqualTo("hello\n");
        assertThat(out.timedOut()).isFalse();
    }

    @Test
    void wallClockTimeoutKillsContainer() {
        var limits = new RunLimits(Duration.ofMillis(500), 65536, 16_777_216, 64, 64);
        var out = runner.runOnce("ocee/sandbox-python:3.11", "python3 -c 'while True: pass'",
                volume, true, null, null, limits, token, "run");
        assertThat(out.timedOut()).isTrue();
    }

    @Test
    void oomKilledFlagsOom() {
        var limits = new RunLimits(Duration.ofSeconds(5), 32_768, 16_777_216, 64, 64);
        var out = runner.runOnce("ocee/sandbox-python:3.11",
                "python3 -c \"a=bytearray(64*1024*1024)\nfor i in range(0,len(a),4096): a[i]=1\nprint(len(a))\"",
                volume, true, null, null, limits, token, "run");
        assertThat(out.oomKilled()).isTrue();
    }

    @Test
    void putArchiveSourceAndRunIt() {
        var limits = new RunLimits(Duration.ofSeconds(5), 65536, 16_777_216, 64, 64);
        var out = runner.runOnce("ocee/sandbox-python:3.11", "python3 main.py",
                volume, false,
                List.of(new ContainerRunner.FilePayload("main.py", "print('hi')\n")),
                null, limits, token, "run");
        assertThat(out.exitCode()).isZero();
        assertThat(out.stdout()).isEqualTo("hi\n");
    }
}
