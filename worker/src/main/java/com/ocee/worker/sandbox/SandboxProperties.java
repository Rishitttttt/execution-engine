package com.ocee.worker.sandbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "sandbox")
public record SandboxProperties(
        int concurrency,
        int outputCapBytes,
        long tmpfsBytes,
        Duration reaperInterval,
        Duration reaperMaxAge,
        Docker docker) {

    public int effectiveConcurrency() {
        return concurrency > 0 ? concurrency : Math.min(Runtime.getRuntime().availableProcessors(), 4);
    }

    public record Docker(String socket) {}
}
