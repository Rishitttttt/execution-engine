package com.ocee.worker.sandbox;

public record RunOutcome(
        int exitCode,
        Integer signal,
        boolean oomKilled,
        boolean timedOut,
        boolean dockerError,
        String stdout,
        String stderr,
        boolean outputTruncated,
        double wallClockSeconds,
        Long memoryKib,
        String message,
        Double userCpuSeconds,
        Double sysCpuSeconds,
        Long maxRssKib) {
}
