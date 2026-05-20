package com.ocee.controller;

import com.ocee.DTO.*;
import com.ocee.common.Status;
import com.ocee.config.RequestBodyCapturingFilter;
import com.ocee.pagination.CursorRequest;
import com.ocee.service.IdempotencyChecker;
import com.ocee.service.SubmissionService;
import com.ocee.wait.WaitRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final SubmissionService service;
    private final WaitRegistry waitRegistry;
    private final int maxWait;

    public SubmissionController(SubmissionService service,
                                WaitRegistry waitRegistry,
                                @Value("${ocee.wait.max-timeout-seconds:10}") int maxWait) {
        this.service = service;
        this.waitRegistry = waitRegistry;
        this.maxWait = maxWait;
    }

    @PostMapping
    public ResponseEntity<SubmissionResponse> create(
            @Valid @RequestBody CreateSubmissionRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID idempotencyKey,
            @RequestParam(name = "wait", required = false, defaultValue = "false") boolean wait,
            @RequestParam(name = "timeout", required = false, defaultValue = "5") int timeout,
            HttpServletRequest request) {
        byte[] hash = idempotencyKey == null ? null : computeBodyHash(request);
        if (wait) {
            SubmissionResponse r = service.createAndWait(body, idempotencyKey, hash, timeout, waitRegistry, maxWait);
            HttpStatus status = r.status().code() == Status.QUEUED.getCode() || r.status().code() == Status.PROCESS.getCode()
                    ? HttpStatus.OK : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(r);
        }
        SubmissionResponse created = service.create(body, idempotencyKey, hash);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private static byte[] computeBodyHash(HttpServletRequest request) {
        Object o = request.getAttribute(RequestBodyCapturingFilter.BODY_WRAPPER_ATTR);
        if (!(o instanceof ContentCachingRequestWrapper w)) return new byte[0];
        return IdempotencyChecker.sha256(w.getContentAsByteArray());
    }

    @GetMapping("/{token}")
    public SubmissionResponse get(@PathVariable UUID token) {
        return service.getByToken(token);
    }

    @GetMapping
    public CursorPageResponse<SubmissionSummaryResponse> list(
            @RequestParam(required = false) Status status,
            @RequestParam(required = false, name = "language_id") Integer languageId,
            @RequestParam(required = false, name = "created_after") Instant createdAfter,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return service.list(status, languageId, createdAfter, CursorRequest.of(cursor, limit));
    }
}
