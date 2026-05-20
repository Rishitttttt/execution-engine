package com.ocee.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.common.JobMessage;
import com.ocee.common.Status;
import com.ocee.entity.Outbox;
import com.ocee.repository.OutboxRepository;
import com.ocee.repository.SubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class OutboxDrainer {

    private static final Logger log = LoggerFactory.getLogger(OutboxDrainer.class);

    private final OutboxRepository outbox;
    private final SubmissionRepository submissions;
    private final JobPublisher publisher;
    private final ObjectMapper mapper;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxDrainer(OutboxRepository outbox,
                         SubmissionRepository submissions,
                         JobPublisher publisher,
                         @Qualifier("streamObjectMapper") ObjectMapper mapper,
                         @Value("${ocee.outbox.batch-size:50}") int batchSize,
                         @Value("${ocee.outbox.max-attempts:5}") int maxAttempts) {
        this.outbox = outbox;
        this.submissions = submissions;
        this.publisher = publisher;
        this.mapper = mapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${ocee.outbox.poll-interval-ms:200}")
    @Transactional
    public void drain() {
        List<Outbox> ready = outbox.findReadyForDelivery(Instant.now(), Limit.of(batchSize));
        for (Outbox row : ready) {
            try {
                JobMessage msg = mapper.readValue(row.getPayload(), JobMessage.class);
                publisher.publish(msg);
                outbox.delete(row);
            } catch (JsonProcessingException e) {
                log.error("outbox row {} has unparseable payload — dropping and marking BOXERR", row.getId(), e);
                markBoxErr(row);
                outbox.delete(row);
            } catch (RuntimeException e) {
                int attempts = row.getAttempts() + 1;
                row.setAttempts(attempts);
                row.setNextAttempt(Instant.now().plus(backoffSeconds(attempts), ChronoUnit.SECONDS));
                if (attempts >= maxAttempts) {
                    log.error("outbox row {} exceeded max-attempts={} — marking submission BOXERR", row.getId(), maxAttempts, e);
                    markBoxErr(row);
                    outbox.delete(row);
                } else {
                    log.warn("outbox row {} delivery failed (attempt {}); will retry", row.getId(), attempts, e);
                    outbox.save(row);
                }
            }
        }
    }

    private long backoffSeconds(int attempts) {
        return Math.min(60L, 1L << Math.min(attempts, 6));
    }

    private void markBoxErr(Outbox row) {
        submissions.findById(row.getSubmissionId()).ifPresent(s -> {
            s.setStatus(Status.BOXERR);
            s.setMessage("Outbox delivery failed");
            s.setFinishedAt(Instant.now());
        });
    }
}
