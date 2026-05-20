package com.ocee.repository;

import com.ocee.entity.Outbox;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /**
     * Pulls up to `limit` outbox rows whose next_attempt has elapsed,
     * locking them with FOR UPDATE SKIP LOCKED so concurrent drainers
     * never grab the same row.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT o FROM Outbox o WHERE o.nextAttempt <= :now ORDER BY o.nextAttempt ASC")
    List<Outbox> findReadyForDelivery(@Param("now") Instant now, Limit limit);
}
