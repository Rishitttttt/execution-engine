package com.ocee.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.DTO.*;
import com.ocee.common.JobMessage;
import com.ocee.common.Status;
import com.ocee.entity.Language;
import com.ocee.entity.Outbox;
import com.ocee.entity.Submission;
import com.ocee.exception.SubmissionNotFoundException;
import com.ocee.mapper.SubmissionMapper;
import com.ocee.pagination.CursorCodec;
import com.ocee.pagination.CursorRequest;
import com.ocee.repository.OutboxRepository;
import com.ocee.repository.SubmissionRepository;
import com.ocee.repository.SubmissionSpecifications;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SubmissionService {
    private final SubmissionRepository submissions;
    private final OutboxRepository outbox;
    private final LanguageService languages;
    private final ResourceLimitsResolver limits;
    private final TokenGenerator tokenGen;
    private final SubmissionMapper mapper;
    private final ObjectMapper streamMapper;
    private final IdempotencyChecker idempotency;

    public SubmissionService(SubmissionRepository submissions,
                             OutboxRepository outbox,
                             LanguageService languages,
                             ResourceLimitsResolver limits,
                             TokenGenerator tokenGen,
                             SubmissionMapper mapper,
                             @Qualifier("streamObjectMapper") ObjectMapper streamMapper,
                             IdempotencyChecker idempotency) {
        this.submissions = submissions;
        this.outbox = outbox;
        this.languages = languages;
        this.limits = limits;
        this.tokenGen = tokenGen;
        this.mapper = mapper;
        this.streamMapper = streamMapper;
        this.idempotency = idempotency;
    }

    @Transactional
    public SubmissionResponse create(CreateSubmissionRequest req, UUID idempotencyKey, byte[] bodyHash) {
        if (idempotencyKey != null) {
            var existing = idempotency.findExisting(idempotencyKey, bodyHash);
            if (existing.isPresent()) return mapper.toResponse(existing.get());
        }

        Language lang = languages.requireActive(req.languageId());
        Submission s = new Submission();
        s.setToken(tokenGen.newToken());
        s.setLanguage(lang);
        s.setSourceCode(req.sourceCode());
        s.setStdIn(req.stdIn());
        s.setExpectedOutput(req.expectedOutput());
        s.setStatus(Status.QUEUED);
        s.setIdempotencyKey(idempotencyKey);
        s.setIdempotencyBodySha256(bodyHash);
        s.setCallbackUrl(req.callbackUrl());
        limits.apply(req, lang, s);

        Submission saved = submissions.save(s);

        JobMessage msg = new JobMessage(
                saved.getToken().toString(),
                lang.getId(),
                lang.getRunCommand(),
                lang.getCompileCommand(),
                lang.getSourceFile(),
                saved.getSourceCode(),
                saved.getStdIn(),
                saved.getExpectedOutput(),
                saved.getCpuTimeLimit(),
                saved.getCpuExtraTimeLimit(),
                saved.getWallTimeLimit(),
                saved.getMemoryLimit(),
                saved.getStackLimit(),
                saved.getMaxProcessesAndOrThreadsLimit(),
                saved.getMaxFileSizeLimit(),
                null,
                lang.getImage(),
                lang.getCompileCpuTime(),
                lang.getCompileMemory()
        );

        Outbox o = new Outbox();
        o.setSubmissionId(saved.getId());
        try {
            o.setPayload(streamMapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JobMessage for outbox", e);
        }
        outbox.save(o);

        return mapper.toResponse(saved);
    }

    public SubmissionResponse createAndWait(CreateSubmissionRequest req, UUID idempotencyKey, byte[] bodyHash,
                                            int waitSeconds, com.ocee.wait.WaitRegistry waitRegistry,
                                            int maxWaitSeconds) {
        SubmissionResponse initial = create(req, idempotencyKey, bodyHash);
        if (initial.status().code() != Status.QUEUED.getCode()) {
            return initial;
        }
        int effectiveWait = Math.min(Math.max(waitSeconds, 1), maxWaitSeconds);
        java.util.concurrent.CompletableFuture<SubmissionResponse> future =
                waitRegistry.register(initial.token());
        try {
            return future.get(effectiveWait, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            waitRegistry.drop(initial.token());
            return getByToken(initial.token());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            waitRegistry.drop(initial.token());
            return getByToken(initial.token());
        } catch (java.util.concurrent.ExecutionException e) {
            waitRegistry.drop(initial.token());
            throw new IllegalStateException(e.getCause());
        }
    }

    @Transactional(readOnly = true)
    public SubmissionResponse getByToken(UUID token) {
        Submission s = submissions.findByToken(token)
                .orElseThrow(() -> new SubmissionNotFoundException(token.toString()));
        return mapper.toResponse(s);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<SubmissionSummaryResponse> list(
            Status status, Integer languageId, Instant createdAfter, CursorRequest cursor) {

        CursorCodec.Cursor parsed = cursor.cursor() == null ? null : CursorCodec.decode(cursor.cursor());
        Instant cAt = parsed == null ? null : parsed.createdAt();
        Long cId = parsed == null ? null : parsed.id();

        List<Submission> rows = submissions.findAll(
                SubmissionSpecifications.filter(status, languageId, createdAfter, cAt, cId),
                PageRequest.of(0, cursor.limit() + 1)).getContent();

        String nextCursor = null;
        if (rows.size() > cursor.limit()) {
            Submission last = rows.get(cursor.limit() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
            rows = rows.subList(0, cursor.limit());
        }

        return new CursorPageResponse<>(rows.stream().map(mapper::toSummary).toList(), nextCursor);
    }
}
