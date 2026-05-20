package com.ocee.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.common.JobMessage;
import com.ocee.common.Status;
import com.ocee.entity.Submission;
import com.ocee.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class DeadLetterApplier {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterApplier.class);

    private final ObjectMapper mapper;
    private final SubmissionRepository submissions;

    public DeadLetterApplier(@Qualifier("streamObjectMapper") ObjectMapper mapper,
                             SubmissionRepository submissions) {
        this.mapper = mapper;
        this.submissions = submissions;
    }

    @Transactional
    public void apply(String payload, String reason) {
        if (payload == null || payload.isEmpty()) return;
        try {
            JobMessage msg = mapper.readValue(payload, JobMessage.class);
            UUID token = UUID.fromString(msg.token());
            Submission s = submissions.findByToken(token).orElse(null);
            if (s != null && s.getStatus() == Status.QUEUED) {
                s.setStatus(Status.BOXERR);
                s.setMessage(reason);
                s.setFinishedAt(Instant.now());
            }
        } catch (Exception e) {
            log.error("Failed to apply dead-letter (reason={})", reason, e);
        }
    }
}
