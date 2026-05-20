package com.ocee.common;

public record JobMessage(
    String token,
    Integer languageId,
    String runCommand,
    String compileCommand,
    String sourceFile,
    String sourceCode,
    String stdIn,
    String expectedOutput,
    Double cpuTimeLimit,
    Double cpuExtraTimeLimit,
    Double wallTimeLimit,
    Integer memoryLimit,
    Integer stackLimit,
    Integer maxProcessesAndOrThreadsLimit,
    Integer maxFileSizeLimit,
    String traceContext,
    String image,
    Double compileCpuTime,
    Integer compileMemory
) {}
