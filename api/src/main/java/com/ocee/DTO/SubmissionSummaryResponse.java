package com.ocee.DTO;

import java.time.Instant;
import java.util.UUID;

public record SubmissionSummaryResponse(
    UUID token,
    StatusResponse status,
    Integer languageId,
    Instant createdAt,
    Instant finishedAt
) {}
