package com.ocee.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ocee.entity.Submission;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class WebhookEnqueuerTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void noopWhenSubmissionHasNoCallbackUrl() {
        var repo = mock(WebhookDeliveryRepository.class);
        var enq = new WebhookEnqueuer(repo, mapper);
        Submission s = new Submission();
        enq.enqueue(s);
        verifyNoInteractions(repo);
    }

    @Test
    void insertsDeliveryRowWhenCallbackUrlSet() {
        var repo = mock(WebhookDeliveryRepository.class);
        when(repo.save(any(WebhookDelivery.class))).thenAnswer(inv -> inv.getArgument(0));
        var enq = new WebhookEnqueuer(repo, mapper);
        Submission s = new Submission();
        s.setId(42L);
        s.setCallbackUrl("https://example.com/hook");
        s.setToken(UUID.randomUUID());
        enq.enqueue(s);
        verify(repo).save(argThat(d ->
                d.getSubmissionId() == 42L &&
                d.getUrl().equals("https://example.com/hook") &&
                d.getPayload().contains("\"token\"")));
    }
}
