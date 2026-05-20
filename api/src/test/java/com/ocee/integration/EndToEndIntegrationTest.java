package com.ocee.integration;

import com.ocee.TestcontainersConfiguration;
import com.ocee.common.Status;
import com.ocee.worker.config.WorkerProperties;
import com.ocee.worker.executor.MockExecutor;
import com.ocee.worker.sandbox.SandboxProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@org.springframework.test.context.ActiveProfiles("mock-executor")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                properties = {
                    "ocee.worker.visibility-timeout-seconds=5",
                    "ocee.worker.reclaim-interval-ms=2000",
                    "ocee.worker.max-deliveries=3",
                    "ocee.worker.mock-min-ms=20",
                    "ocee.worker.mock-max-ms=50",
                    "ocee.streams.consumer-group=ocee-api"
                })
@Import({TestcontainersConfiguration.class, EndToEndIntegrationTest.WorkerInProcessConfig.class})
class EndToEndIntegrationTest {

    @LocalServerPort int port;
    RestClient client;

    @org.junit.jupiter.api.BeforeEach
    void setup() { client = RestClient.create("http://localhost:" + port); }

    @TestConfiguration
    @ComponentScan(basePackages = {
            "com.ocee.worker.consumer",
            "com.ocee.worker.publisher"
    })
    static class WorkerInProcessConfig {
        @Bean WorkerProperties workerProperties() { return new WorkerProperties(5, 2000, 3, 20, 50); }
        @Bean MockExecutor mockExecutor(WorkerProperties p) { return new MockExecutor(p); }
        @Bean SandboxProperties sandboxProperties() {
            return new SandboxProperties(2, 65536, 67_108_864L,
                    Duration.ofSeconds(60), Duration.ofSeconds(60),
                    new SandboxProperties.Docker("unix:///var/run/docker.sock"));
        }
    }

    @Test
    void submitAndPollUntilTerminal() {
        record Body(int language_id, String source_code) {}
        var post = client.post().uri("/api/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Body(1, "print(1)"))
                .retrieve().toEntity(Map.class);
        String token = (String) post.getBody().get("token");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var get = client.get().uri("/api/submissions/" + token).retrieve().toEntity(Map.class);
            int code = (int) ((Map<?,?>) get.getBody().get("status")).get("code");
            assertThat(code).isNotEqualTo(Status.QUEUED.getCode());
        });
    }

    @Test
    void submitWithWaitTrueBlocksUntilTerminal() {
        record Body(int language_id, String source_code) {}
        var resp = client.post().uri("/api/submissions?wait=true&timeout=10")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Body(1, "print(2)"))
                .retrieve().toEntity(Map.class);
        int code = (int) ((Map<?,?>) resp.getBody().get("status")).get("code");
        assertThat(code).isNotEqualTo(Status.QUEUED.getCode());
        assertThat(code).isNotEqualTo(Status.PROCESS.getCode());
    }

    @Test
    void readyzReturnsReadyWhenDbAndRedisUp() {
        var r = client.get().uri("/api/readyz").retrieve().toEntity(Map.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("status")).isEqualTo("ready");
    }
}
