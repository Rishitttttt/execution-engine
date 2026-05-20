package com.ocee.repository;

import com.ocee.TestcontainersConfiguration;
import com.ocee.common.Status;
import com.ocee.entity.Submission;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SubmissionIdempotencyHashIT {
    @Autowired SubmissionRepository repo;
    @Autowired LanguageRepository langs;

    @Test
    void persistsAndRetrievesBodyHash() {
        Submission s = new Submission();
        s.setToken(UUID.randomUUID());
        s.setLanguage(langs.findById(1).orElseThrow());
        s.setSourceCode("x");
        s.setStatus(Status.QUEUED);
        s.setIdempotencyBodySha256(new byte[]{1, 2, 3, 4});
        s.setCallbackUrl("https://example.com/hook");
        repo.saveAndFlush(s);
        var loaded = repo.findById(s.getId()).orElseThrow();
        assertThat(loaded.getIdempotencyBodySha256()).containsExactly(1, 2, 3, 4);
        assertThat(loaded.getCallbackUrl()).isEqualTo("https://example.com/hook");
    }
}
