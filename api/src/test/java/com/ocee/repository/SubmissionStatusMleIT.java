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
class SubmissionStatusMleIT {
    @Autowired SubmissionRepository repo;
    @Autowired LanguageRepository languages;

    @Test
    void canPersistMleStatus() {
        Submission s = new Submission();
        s.setToken(UUID.randomUUID());
        s.setLanguage(languages.findById(1).orElseThrow());
        s.setSourceCode("print(1)");
        s.setStatus(Status.MLE);
        repo.saveAndFlush(s);
        assertThat(repo.findById(s.getId()).orElseThrow().getStatus()).isEqualTo(Status.MLE);
    }
}
