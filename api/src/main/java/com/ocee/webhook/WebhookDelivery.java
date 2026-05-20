package com.ocee.webhook;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "webhook_delivery")
@Getter @Setter @NoArgsConstructor
public class WebhookDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "submission_id", nullable = false) private Long submissionId;
    @Column(nullable = false) private String url;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb") private String payload;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt", nullable = false) private Instant nextAttempt;
    @Column(name = "last_error") private String lastError;
    @Column(name = "last_status") private Integer lastStatus;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
}
