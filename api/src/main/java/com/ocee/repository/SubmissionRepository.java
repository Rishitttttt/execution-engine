package com.ocee.repository;

import com.ocee.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository
        extends JpaRepository<Submission, Long>, JpaSpecificationExecutor<Submission> {

    Optional<Submission> findByToken(UUID token);

    Optional<Submission> findByIdempotencyKey(UUID idempotencyKey);
}
