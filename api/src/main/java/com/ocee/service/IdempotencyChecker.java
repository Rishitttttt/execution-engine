package com.ocee.service;

import com.ocee.entity.Submission;
import com.ocee.exception.IdempotencyConflictException;
import com.ocee.repository.SubmissionRepository;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Component
public class IdempotencyChecker {
    private final SubmissionRepository repo;
    public IdempotencyChecker(SubmissionRepository repo) { this.repo = repo; }

    public Optional<Submission> findExisting(UUID key, byte[] bodyHash) {
        Optional<Submission> existing = repo.findByIdempotencyKey(key);
        if (existing.isEmpty()) return Optional.empty();
        byte[] stored = existing.get().getIdempotencyBodySha256();
        if (stored == null || !Arrays.equals(stored, bodyHash)) {
            throw new IdempotencyConflictException(key.toString());
        }
        return existing;
    }

    public static byte[] sha256(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
