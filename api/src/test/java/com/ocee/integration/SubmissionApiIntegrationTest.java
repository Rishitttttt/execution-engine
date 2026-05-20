package com.ocee.integration;

import com.ocee.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SubmissionApiIntegrationTest {

    @LocalServerPort int port;
    RestClient client;

    @org.junit.jupiter.api.BeforeEach
    void setUp() { client = RestClient.create("http://localhost:" + port); }

    @Test
    void postSubmission_thenGetByToken_returnsQueued() {
        record Body(int language_id, String source_code) {}
        var post = client.post().uri("/api/submissions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new Body(1, "print(1)"))
                .retrieve().toEntity(Map.class);

        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = post.getBody();
        String token = (String) body.get("token");
        assertThat(token).isNotNull();
        assertThat(((Map<?,?>) body.get("status")).get("code")).isEqualTo(1);

        var get = client.get().uri("/api/submissions/" + token)
                .retrieve().toEntity(Map.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?,?>) get.getBody().get("status")).get("code")).isEqualTo(1);
    }

    @Test
    void postSubmission_unknownLanguage_returns404Problem() {
        record Body(int language_id, String source_code) {}
        try {
            client.post().uri("/api/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Body(999, "x"))
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            assertThat(e.getResponseHeaders().getContentType().toString())
                    .contains("application/problem+json");
            assertThat(e.getResponseBodyAsString()).contains("language-not-found");
            return;
        }
        org.junit.jupiter.api.Assertions.fail("expected 404");
    }

    @Test
    void postSubmission_oversizedSource_returns400ValidationProblem() {
        record Body(int language_id, String source_code) {}
        String huge = "x".repeat(70_000);
        try {
            client.post().uri("/api/submissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Body(1, huge))
                    .retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
            assertThat(e.getResponseBodyAsString()).contains("\"errors\"");
            return;
        }
        org.junit.jupiter.api.Assertions.fail("expected 400");
    }

    @Test
    void listSubmissions_paginatesByCursor() {
        record Body(int language_id, String source_code) {}
        for (int i = 0; i < 3; i++) {
            client.post().uri("/api/submissions").contentType(MediaType.APPLICATION_JSON)
                    .body(new Body(1, "print(" + i + ")")).retrieve().toBodilessEntity();
        }
        var page = client.get().uri("/api/submissions?limit=2").retrieve().toEntity(Map.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (List<?>) page.getBody().get("data");
        assertThat(data).hasSize(2);
        assertThat(page.getBody().get("next_cursor")).isNotNull();
    }

    @Test
    void readyz_returns200WhenDbUp() {
        var resp = client.get().uri("/api/readyz").retrieve().toEntity(Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
