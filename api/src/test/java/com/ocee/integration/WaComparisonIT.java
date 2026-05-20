package com.ocee.integration;

import com.ocee.TestcontainersConfiguration;
import com.ocee.common.Status;
import com.ocee.worker.config.WorkerProperties;
import com.ocee.worker.executor.Executor;
import com.ocee.worker.sandbox.SandboxProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@ActiveProfiles("mock-executor")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {
                    "ocee.worker.visibility-timeout-seconds=5",
                    "ocee.worker.reclaim-interval-ms=2000",
                    "ocee.worker.max-deliveries=3",
                    "ocee.worker.mock-min-ms=20",
                    "ocee.worker.mock-max-ms=50",
                    "ocee.streams.consumer-group=ocee-api"
                })
@Import({TestcontainersConfiguration.class, WaComparisonIT.WorkerInProcessConfig.class})
class WaComparisonIT {
    @LocalServerPort int port;

    @TestConfiguration
    @ComponentScan(basePackages = {"com.ocee.worker.consumer", "com.ocee.worker.publisher"})
    static class WorkerInProcessConfig {
        @Bean WorkerProperties workerProperties() { return new WorkerProperties(5, 2000, 3, 20, 50); }
        @Bean Executor stubExecutor() {
            return msg -> new com.ocee.common.JobResult(
                    msg.token(), Status.AC, "hello\n", null, null, 0, null, null,
                    0.01, 0.01, 1024, "h", Instant.now(), null, false);
        }
        @Bean SandboxProperties sandboxProperties() {
            return new SandboxProperties(2, 65536, 67_108_864L,
                    Duration.ofSeconds(60), Duration.ofSeconds(60),
                    new SandboxProperties.Docker("unix:///var/run/docker.sock"));
        }
    }

    @Test
    void matchingExpectedOutputStaysAc() {
        var c = RestClient.create("http://localhost:" + port);
        var post = c.post().uri("/api/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("language_id", 1, "source_code", "print('hello')",
                        "expected_output", "hello"))
                .retrieve().toEntity(Map.class);
        String token = (String) post.getBody().get("token");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var get = c.get().uri("/api/submissions/" + token).retrieve().toEntity(Map.class);
            int code = (int) ((Map<?,?>) get.getBody().get("status")).get("code");
            assertThat(code).isEqualTo(Status.AC.getCode());
        });
    }

    @Test
    void mismatchingExpectedOutputBecomesWa() {
        var c = RestClient.create("http://localhost:" + port);
        var post = c.post().uri("/api/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("language_id", 1, "source_code", "print('hello')",
                        "expected_output", "world"))
                .retrieve().toEntity(Map.class);
        String token = (String) post.getBody().get("token");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var get = c.get().uri("/api/submissions/" + token).retrieve().toEntity(Map.class);
            int code = (int) ((Map<?,?>) get.getBody().get("status")).get("code");
            assertThat(code).isEqualTo(Status.WA.getCode());
        });
    }
}
