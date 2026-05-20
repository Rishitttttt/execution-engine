package com.ocee.repository;

import com.ocee.TestcontainersConfiguration;
import com.ocee.common.Status;
import com.ocee.entity.Language;
import com.ocee.entity.Submission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class SubmissionRepositoryTest {

    @Autowired SubmissionRepository submissions;
    @Autowired LanguageRepository languages;
    @Autowired OutboxRepository outbox;

    Language python;

    @BeforeEach
    void setUp() {
        outbox.deleteAllInBatch();
        submissions.deleteAllInBatch();
        python = languages.findById(1).orElseThrow();
    }

    private Submission make(String code, Status status) {
        Submission s = new Submission();
        s.setToken(UUID.randomUUID());
        s.setLanguage(python);
        s.setSourceCode(code);
        s.setStatus(status);
        return submissions.save(s);
    }

    @Test
    void findByTokenReturnsTheRow() {
        Submission saved = make("print(1)", Status.QUEUED);
        assertThat(submissions.findByToken(saved.getToken()))
                .get().extracting(Submission::getSourceCode).isEqualTo("print(1)");
    }

    @Test
    void findPageReturnsNewestFirstWithFiltersAndCursor() {
        for (int i = 0; i < 10; i++) make("code-" + i, Status.QUEUED);
        for (int i = 0; i < 5;  i++) make("ac-"   + i, Status.AC);

        // page 1: limit 5, no filter
        List<Submission> page1 = submissions.findAll(
                SubmissionSpecifications.filter(null, null, null, null, null),
                PageRequest.of(0, 5)).getContent();
        assertThat(page1).hasSize(5);

        // cursor to page 2
        Submission last = page1.get(4);
        List<Submission> page2 = submissions.findAll(
                SubmissionSpecifications.filter(null, null, null, last.getCreatedAt(), last.getId()),
                PageRequest.of(0, 5)).getContent();
        assertThat(page2).hasSize(5).doesNotContainAnyElementsOf(page1);

        // status filter
        List<Submission> onlyAc = submissions.findAll(
                SubmissionSpecifications.filter(Status.AC, null, null, null, null),
                PageRequest.of(0, 50)).getContent();
        assertThat(onlyAc).hasSize(5).allMatch(s -> s.getStatus() == Status.AC);
    }
}
