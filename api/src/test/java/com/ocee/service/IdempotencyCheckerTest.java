package com.ocee.service;

import com.ocee.entity.Submission;
import com.ocee.exception.IdempotencyConflictException;
import com.ocee.repository.SubmissionRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdempotencyCheckerTest {
    private final SubmissionRepository repo = mock(SubmissionRepository.class);
    private final IdempotencyChecker checker = new IdempotencyChecker(repo);

    @Test void missReturnsEmpty() {
        when(repo.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        assertThat(checker.findExisting(UUID.randomUUID(), new byte[]{1})).isEmpty();
    }
    @Test void hitWithMatchingHashReturnsExisting() {
        UUID k = UUID.randomUUID();
        Submission s = new Submission();
        s.setIdempotencyKey(k);
        s.setIdempotencyBodySha256(new byte[]{1, 2, 3});
        when(repo.findByIdempotencyKey(k)).thenReturn(Optional.of(s));
        assertThat(checker.findExisting(k, new byte[]{1, 2, 3})).contains(s);
    }
    @Test void hitWithDifferentHashThrowsConflict() {
        UUID k = UUID.randomUUID();
        Submission s = new Submission();
        s.setIdempotencyKey(k);
        s.setIdempotencyBodySha256(new byte[]{1, 2, 3});
        when(repo.findByIdempotencyKey(k)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> checker.findExisting(k, new byte[]{9, 9, 9}))
                .isInstanceOf(IdempotencyConflictException.class);
    }
    @Test void hashLengthIs32() {
        assertThat(IdempotencyChecker.sha256("{\"a\":1}".getBytes())).hasSize(32);
    }
}
