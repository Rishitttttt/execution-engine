package com.ocee.webhook;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ocee.TestcontainersConfiguration;
import com.ocee.common.Status;
import com.ocee.entity.Submission;
import com.ocee.repository.LanguageRepository;
import com.ocee.repository.SubmissionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WebhookDrainerIT {
    static WireMockServer wm;

    @Autowired WebhookDeliveryRepository repo;
    @Autowired WebhookDeliveryDeadRepository deadRepo;
    @Autowired SubmissionRepository submissions;
    @Autowired LanguageRepository languages;

    @BeforeAll static void up() { wm = new WireMockServer(options().dynamicPort()); wm.start(); }
    @AfterAll  static void down() { wm.stop(); }
    @AfterEach void reset() {
        wm.resetAll();
        repo.deleteAllInBatch();
        deadRepo.deleteAllInBatch();
    }

    @Test
    void successfulDeliveryDeletesRow() {
        wm.stubFor(post("/hook").willReturn(aResponse().withStatus(200)));
        WebhookDelivery d = newDelivery(wm.baseUrl() + "/hook");
        repo.saveAndFlush(d);
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(repo.count()).isZero());
        wm.verify(postRequestedFor(urlEqualTo("/hook")));
    }

    @Test
    void retriesOn500ThenDeadLetters() {
        wm.stubFor(post("/hook").willReturn(aResponse().withStatus(500)));
        WebhookDelivery d = newDelivery(wm.baseUrl() + "/hook");
        repo.saveAndFlush(d);
        for (int i = 0; i < 8; i++) {
            await().atMost(Duration.ofSeconds(8)).until(() -> {
                var rows = repo.findAll();
                if (rows.isEmpty()) return true;
                rows.forEach(r -> { r.setNextAttempt(Instant.now()); repo.saveAndFlush(r); });
                return false;
            });
            if (deadRepo.count() == 1) return;
        }
        assertThat(deadRepo.count()).isEqualTo(1);
    }

    private WebhookDelivery newDelivery(String url) {
        Submission s = new Submission();
        s.setToken(UUID.randomUUID());
        s.setLanguage(languages.findById(1).orElseThrow());
        s.setSourceCode("x");
        s.setStatus(Status.AC);
        submissions.saveAndFlush(s);

        WebhookDelivery d = new WebhookDelivery();
        d.setSubmissionId(s.getId());
        d.setUrl(url);
        d.setPayload("{\"token\":\"" + s.getToken() + "\",\"status\":{\"code\":3}}");
        d.setAttempts(0);
        d.setNextAttempt(Instant.now());
        return d;
    }
}
