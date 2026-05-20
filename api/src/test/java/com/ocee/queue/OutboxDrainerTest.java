package com.ocee.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.TestcontainersConfiguration;
import com.ocee.common.JobMessage;
import com.ocee.common.Status;
import com.ocee.entity.Language;
import com.ocee.entity.Outbox;
import com.ocee.entity.Submission;
import com.ocee.repository.LanguageRepository;
import com.ocee.repository.OutboxRepository;
import com.ocee.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxDrainerTest {

    @Autowired SubmissionRepository submissions;
    @Autowired OutboxRepository outbox;
    @Autowired LanguageRepository languages;
    @Autowired OutboxDrainer drainer;
    @Autowired StringRedisTemplate redis;
    @Autowired @Qualifier("streamObjectMapper") ObjectMapper mapper;

    @Test
    void drainPublishesOutboxRowToRedisAndRemovesIt() throws Exception {
        Language python = languages.findById(1).orElseThrow();
        Submission s = new Submission();
        s.setToken(UUID.randomUUID());
        s.setLanguage(python);
        s.setSourceCode("print(1)");
        s.setStatus(Status.QUEUED);
        s = submissions.save(s);

        JobMessage msg = new JobMessage(s.getToken().toString(), 1,
                "python3 main.py", null, "main.py", "print(1)", null, null,
                2.0, null, null, 128000, null, null, null, null,
                "ocee/sandbox-python:3.11", 10.0, 524288);
        Outbox row = new Outbox();
        row.setSubmissionId(s.getId());
        row.setPayload(mapper.writeValueAsString(msg));
        outbox.save(row);

        drainer.drain();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(outbox.count()).isZero();
            Long len = redis.opsForStream().size("ocee.jobs");
            assertThat(len).isGreaterThanOrEqualTo(1L);
        });
    }
}
