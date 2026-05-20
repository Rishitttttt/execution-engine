package com.ocee.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.entity.Submission;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WebhookEnqueuer {
    private final WebhookDeliveryRepository repo;
    private final ObjectMapper mapper;

    public WebhookEnqueuer(WebhookDeliveryRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public void enqueue(Submission s) {
        if (s.getCallbackUrl() == null || s.getCallbackUrl().isBlank()) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", s.getToken().toString());
        if (s.getStatus() != null) {
            body.put("status", Map.of(
                    "code", s.getStatus().getCode(),
                    "description", s.getStatus().getDescription()));
        }
        body.put("std_out", s.getStdOut());
        body.put("std_err", s.getStdErr());
        body.put("compile_output", s.getCompileOutput());
        body.put("exit_code", s.getExitCode());
        body.put("time", s.getTime());
        body.put("wall_time", s.getWallTime());
        body.put("memory", s.getMemory());
        body.put("finished_at", s.getFinishedAt());

        WebhookDelivery d = new WebhookDelivery();
        d.setSubmissionId(s.getId());
        d.setUrl(s.getCallbackUrl());
        try { d.setPayload(mapper.writeValueAsString(body)); }
        catch (Exception e) { throw new IllegalStateException("Failed to serialize webhook payload", e); }
        d.setAttempts(0);
        d.setNextAttempt(Instant.now());
        repo.save(d);
    }
}
