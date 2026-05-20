package com.ocee.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ocee.worker")
public record WorkerProperties(
        long visibilityTimeoutSeconds,
        long reclaimIntervalMs,
        int maxDeliveries,
        long mockMinMs,
        long mockMaxMs) {}
