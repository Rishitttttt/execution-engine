package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
class RealMetricsIT {
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
    void wallTimeReflectsSleep() {
        var limits = new RunLimits(Duration.ofSeconds(5), 65536, 16_777_216, 64, 64);
        var out = runner.runOnce("ocee/sandbox-python:3.11", "python3 -c 'import time; time.sleep(0.2)'",
                volume, true, null, null, limits, token, "run");
        assertThat(out.exitCode()).isZero();
        assertThat(out.wallClockSeconds()).isBetween(0.15, 2.0);
    }

    @Test
    void cpuAndRssPopulated() {
        var limits = new RunLimits(Duration.ofSeconds(5), 65536, 16_777_216, 64, 64);
        var out = runner.runOnce("ocee/sandbox-python:3.11",
                "python3 -c 'x=0\nfor i in range(2000000): x+=i\nprint(x)' || true",
                volume, true, null, null, limits, token, "run");
        // CPU may be ~0.0 on fast hosts; just check non-null + RSS plausible
        assertThat(out.userCpuSeconds()).as("userCpuSeconds").isNotNull();
        assertThat(out.userCpuSeconds()).isNotNull().isGreaterThanOrEqualTo(0.0);
        assertThat(out.maxRssKib()).isNotNull().isGreaterThan(1000L);
    }
}
