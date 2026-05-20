package com.ocee.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {
    @Query(value = """
            SELECT * FROM webhook_delivery
             WHERE next_attempt <= :now
             ORDER BY next_attempt
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookDelivery> claimDue(@Param("now") Instant now, @Param("limit") int limit);
}
