package com.ocee.DTO;

import java.time.Instant;
import java.util.UUID;

public record SubmissionResponse(
    UUID token,
    StatusResponse status,
    Integer languageId,
    String sourceCode,
    String stdIn,
    String expectedOutput,
    String stdOut,
    String stdErr,
    String compileOutput,
    String message,
    Integer exitCode,
    Integer exitSignal,
    Double time,
    Double wallTime,
    Integer memory,
    String executionHost,
    Double cpuTimeLimit,
    Double cpuExtraTimeLimit,
    Double wallTimeLimit,
    Integer memoryLimit,
    Integer stackLimit,
    Integer maxProcessesAndOrThreadsLimit,
    Integer maxFileSizeLimit,
    Instant createdAt,
    Instant finishedAt
) {}
