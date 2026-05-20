package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("docker")
class VolumeManagerIT {
    static DockerClient docker;
    VolumeManager vm;
    String token;

    @BeforeAll static void up() {
        var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        docker = DockerClientImpl.getInstance(cfg,
                new ZerodepDockerHttpClient.Builder().dockerHost(cfg.getDockerHost()).build());
    }
    @AfterAll static void down() throws Exception { docker.close(); }

    @BeforeEach void each() { vm = new VolumeManager(docker, "test-host"); token = UUID.randomUUID().toString(); }
    @AfterEach  void cleanup() { try { vm.remove(token); } catch (Exception ignored) {} }

    @Test
    void createPutArchiveAndRemove() {
        String volume = vm.create(token);
        assertThat(volume).isEqualTo("ocee-sandbox-" + token);
        vm.remove(token);
        assertThat(docker.listVolumesCmd().exec().getVolumes()).noneMatch(v -> volume.equals(v.getName()));
    }
}
