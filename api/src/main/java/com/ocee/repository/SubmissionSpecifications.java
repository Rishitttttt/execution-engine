package com.ocee.repository;

import com.ocee.common.Status;
import com.ocee.entity.Submission;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SubmissionSpecifications {

    private SubmissionSpecifications() {}

    /**
     * Builds a Specification with optional filters and an exclusive cursor.
     * Each non-null parameter contributes a predicate; null parameters are skipped.
     * Always orders newest-first by (created_at, id) descending.
     */
    public static Specification<Submission> filter(
            Status status,
            Integer languageId,
            Instant createdAfter,
            Instant cursorCreatedAt,
            Long cursorId) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (status != null) preds.add(cb.equal(root.get("status"), status));
            if (languageId != null) preds.add(cb.equal(root.get("language").get("id"), languageId));
            if (createdAfter != null) preds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
            if (cursorCreatedAt != null && cursorId != null) {
                Predicate strictlyEarlier = cb.lessThan(root.get("createdAt"), cursorCreatedAt);
                Predicate sameInstantEarlierId = cb.and(
                        cb.equal(root.get("createdAt"), cursorCreatedAt),
                        cb.lessThan(root.get("id"), cursorId));
                preds.add(cb.or(strictlyEarlier, sameInstantEarlierId));
            }
            if (query != null) {
                query.orderBy(cb.desc(root.get("createdAt")), cb.desc(root.get("id")));
            }
            return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(Predicate[]::new));
        };
    }
}
