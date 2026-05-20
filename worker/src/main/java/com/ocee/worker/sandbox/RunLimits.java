package com.ocee.worker.sandbox;

import java.time.Duration;

public record RunLimits(
        Duration wallClock,
        long memoryKib,
        long tmpfsBytes,
        int pidsLimit,
        int nofile) {

    public static RunLimits run(double cpuTimeSeconds, int memoryKib, long tmpfsBytes) {
        return new RunLimits(
                Duration.ofMillis((long)(cpuTimeSeconds * 1000)),
                memoryKib, tmpfsBytes, 128, 64);
    }
    public static RunLimits compile(double cpuTimeSeconds, int memoryKib, long tmpfsBytes) {
        return new RunLimits(
                Duration.ofMillis((long)(cpuTimeSeconds * 1000)),
                memoryKib, tmpfsBytes, 256, 256);
    }
}
