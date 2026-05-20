package com.ocee.controller;

import com.ocee.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SubmissionControllerIdempotencyTest {
    @LocalServerPort int port;

    @Test
    void sameKeySameBodyReturnsOriginalToken() {
        var c = RestClient.create("http://localhost:" + port);
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("language_id", 1, "source_code", "print(1)");
        var first = c.post().uri("/api/submissions")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(Map.class);
        var second = c.post().uri("/api/submissions")
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(Map.class);
        assertThat(first.getBody().get("token")).isEqualTo(second.getBody().get("token"));
    }

    @Test
    void sameKeyDifferentBodyReturns409() {
        var c = RestClient.create("http://localhost:" + port);
        String key = UUID.randomUUID().toString();
        c.post().uri("/api/submissions").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("language_id", 1, "source_code", "print(1)"))
                .retrieve().toEntity(Map.class);
        assertThatThrownBy(() ->
                c.post().uri("/api/submissions").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("language_id", 1, "source_code", "print(2)"))
                        .retrieve().toEntity(Map.class))
                .isInstanceOf(HttpClientErrorException.class)
                .hasMessageContaining("409");
    }
}
