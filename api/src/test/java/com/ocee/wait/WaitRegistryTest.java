package com.ocee.wait;

import com.ocee.DTO.StatusResponse;
import com.ocee.DTO.SubmissionResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

class WaitRegistryTest {

    private final WaitRegistry registry = new WaitRegistry();

    private SubmissionResponse stub(UUID token) {
        return new SubmissionResponse(token, new StatusResponse(3, "Accepted"), 1, "x",
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @Test
    void completeUnblocksRegisteredWaiter() throws Exception {
        UUID token = UUID.randomUUID();
        CompletableFuture<SubmissionResponse> f = registry.register(token);

        registry.complete(token, stub(token));

        SubmissionResponse r = f.get(1, TimeUnit.SECONDS);
        assertThat(r.token()).isEqualTo(token);
    }

    @Test
    void completeWithoutWaiterIsNoOp() {
        registry.complete(UUID.randomUUID(), stub(UUID.randomUUID()));
    }

    @Test
    void timeoutDropRemovesWaiterFromRegistry() {
        UUID token = UUID.randomUUID();
        registry.register(token);
        registry.drop(token);
        assertThat(registry.peek(token)).isEmpty();
    }

    @Test
    void getOnUncompletedFutureTimesOut() {
        UUID token = UUID.randomUUID();
        CompletableFuture<SubmissionResponse> f = registry.register(token);
        assertThatThrownBy(() -> f.get(50, TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);
    }
}
