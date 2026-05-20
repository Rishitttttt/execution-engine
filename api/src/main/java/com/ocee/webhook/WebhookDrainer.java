package com.ocee.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class WebhookDrainer {
    private static final Logger log = LoggerFactory.getLogger(WebhookDrainer.class);
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(1), Duration.ofSeconds(5),
            Duration.ofSeconds(30), Duration.ofMinutes(5),
            Duration.ofMinutes(5)
    };
    private static final int MAX_ATTEMPTS = BACKOFF.length;
    private static final int BATCH = 50;

    private final WebhookDeliveryRepository repo;
    private final WebhookDeliveryDeadRepository deadRepo;
    private final RestClient http;

    public WebhookDrainer(WebhookDeliveryRepository repo, WebhookDeliveryDeadRepository deadRepo) {
        this.repo = repo;
        this.deadRepo = deadRepo;
        this.http = RestClient.builder().build();
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void drain() {
        List<WebhookDelivery> due = repo.claimDue(Instant.now(), BATCH);
        for (WebhookDelivery d : due) attemptDelivery(d);
    }

    private void attemptDelivery(WebhookDelivery d) {
        int attempt = d.getAttempts() + 1;
        try {
            var resp = http.post().uri(d.getUrl())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "OCEE-Webhook/1.0")
                    .header("X-OCEE-Submission-Token", extractToken(d.getPayload()))
                    .header("X-OCEE-Delivery-Attempt", Integer.toString(attempt))
                    .body(d.getPayload())
                    .retrieve().toBodilessEntity();
            if (resp.getStatusCode().is2xxSuccessful()) {
                repo.delete(d);
                return;
            }
            handleFailure(d, attempt, "non-2xx", resp.getStatusCode().value());
        } catch (Exception e) {
            handleFailure(d, attempt, e.getClass().getSimpleName() + ": " + e.getMessage(), null);
        }
    }

    private void handleFailure(WebhookDelivery d, int attempt, String error, Integer status) {
        if (attempt >= MAX_ATTEMPTS) {
            WebhookDeliveryDead dead = new WebhookDeliveryDead();
            dead.setSubmissionId(d.getSubmissionId());
            dead.setUrl(d.getUrl());
            dead.setPayload(d.getPayload());
            dead.setAttempts(attempt);
            dead.setLastError(error);
            dead.setLastStatus(status);
            deadRepo.save(dead);
            repo.delete(d);
            log.warn("Webhook dead-lettered: submission={} url={} after {} attempts",
                    d.getSubmissionId(), d.getUrl(), attempt);
        } else {
            d.setAttempts(attempt);
            d.setLastError(error);
            d.setLastStatus(status);
            d.setNextAttempt(Instant.now().plus(BACKOFF[attempt - 1]));
            repo.save(d);
        }
    }

    private static String extractToken(String payload) {
        int i = payload.indexOf("\"token\":\"");
        if (i < 0) return "";
        int start = i + 9;
        int end = payload.indexOf('"', start);
        return end > start ? payload.substring(start, end) : "";
    }
}
