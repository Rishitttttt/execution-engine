package com.ocee.common;

import java.time.Instant;

public record JobResult(
    String token,
    Status status,
    String stdOut,
    String stdErr,
    String compileOutput,
    Integer exitCode,
    Integer exitSignal,
    String message,
    Double time,
    Double wallTime,
    Integer memory,
    String executionHost,
    Instant finishedAt,
    String traceContext,
    boolean outputTruncated
) {}
