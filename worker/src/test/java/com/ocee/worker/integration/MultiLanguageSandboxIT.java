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
class MultiLanguageSandboxIT {
    static DockerClient docker;
    SandboxOrchestrator orch;
    VolumeManager vm;
    ContainerRunner cr;

    @BeforeAll static void up() {
        var cfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        docker = DockerClientImpl.getInstance(cfg,
                new ZerodepDockerHttpClient.Builder().dockerHost(cfg.getDockerHost()).build());
    }
    @AfterAll static void down() throws Exception { docker.close(); }
    @BeforeEach void each() {
        vm = new VolumeManager(docker, "it-host");
        cr = new ContainerRunner(docker, "it-host", 65536);
        orch = new SandboxOrchestrator(vm, cr, "it-host", 65536, 16_777_216L);
    }

    private JobMessage build(int id, String image, String src, String compile, String runCmd, String file) {
        return new JobMessage(UUID.randomUUID().toString(), id, runCmd, compile,
                file, src, "", null, 3.0, 0.5, 6.0, 262144,
                null, null, null, null, image, 10.0, 524288);
    }

    @Test void cAc() {
        var src = "#include <stdio.h>\nint main(){printf(\"hi\\n\");return 0;}";
        var r = orch.execute(build(2, "ocee/sandbox-c:13", src, "gcc -O2 main.c -o main", "./main", "main.c"),
                "ocee/sandbox-c:13", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.AC);
        assertThat(r.stdOut()).isEqualTo("hi\n");
    }
    @Test void cCe() {
        var src = "int main(){return foo;}";
        var r = orch.execute(build(2, "ocee/sandbox-c:13", src, "gcc -O2 main.c -o main", "./main", "main.c"),
                "ocee/sandbox-c:13", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.CE);
        assertThat(r.compileOutput()).contains("error");
    }
    @Test void cppAc() {
        var src = "#include <iostream>\nint main(){std::cout<<\"hi\\n\";}";
        var r = orch.execute(build(3, "ocee/sandbox-cpp:13", src, "g++ -std=c++20 -O2 main.cpp -o main", "./main", "main.cpp"),
                "ocee/sandbox-cpp:13", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.AC);
        assertThat(r.stdOut()).isEqualTo("hi\n");
    }
    @Test void nodeAc() {
        var r = orch.execute(build(5, "ocee/sandbox-node:20", "console.log('hi')", null, "node main.js", "main.js"),
                "ocee/sandbox-node:20", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.AC);
        assertThat(r.stdOut()).isEqualTo("hi\n");
    }
    @Test void nodeNzec() {
        var r = orch.execute(build(5, "ocee/sandbox-node:20", "throw new Error('boom')", null, "node main.js", "main.js"),
                "ocee/sandbox-node:20", 10.0, 524288);
        assertThat(r.status()).isEqualTo(Status.NZEC);
    }
}
