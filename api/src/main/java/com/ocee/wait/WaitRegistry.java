package com.ocee.wait;

import com.ocee.DTO.SubmissionResponse;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class WaitRegistry {

    private final ConcurrentMap<UUID, CompletableFuture<SubmissionResponse>> waiters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanup =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "wait-registry-cleanup");
                t.setDaemon(true);
                return t;
            });

    /** Returns the future associated with this token (creating it if absent). Idempotent. */
    public CompletableFuture<SubmissionResponse> register(UUID token) {
        return waiters.computeIfAbsent(token, k -> new CompletableFuture<>());
    }

    /**
     * Completes the waiter for this token. If no waiter is registered yet, the result is parked
     * (a completed future is left in the map) so that a subsequent register() returns it
     * immediately. Parked entries are evicted after 30s to prevent leaks.
     */
    public void complete(UUID token, SubmissionResponse response) {
        CompletableFuture<SubmissionResponse> f = waiters.computeIfAbsent(token, k -> new CompletableFuture<>());
        f.complete(response);
        cleanup.schedule(() -> waiters.remove(token, f), 30, TimeUnit.SECONDS);
    }

    /** Drops a waiter (e.g. on caller-side timeout). Safe to call even if not present. */
    public void drop(UUID token) {
        waiters.remove(token);
    }

    public Optional<CompletableFuture<SubmissionResponse>> peek(UUID token) {
        return Optional.ofNullable(waiters.get(token));
    }

    @PreDestroy
    void shutdown() {
        cleanup.shutdownNow();
    }
}
