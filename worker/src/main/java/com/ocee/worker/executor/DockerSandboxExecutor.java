package com.ocee.worker.executor;

import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.common.Status;
import com.ocee.worker.sandbox.ImageCache;
import com.ocee.worker.sandbox.SandboxImageMissingException;
import com.ocee.worker.sandbox.SandboxOrchestrator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Instant;

@Component
@Profile("!mock-executor")
public class DockerSandboxExecutor implements Executor {

    private final SandboxOrchestrator orchestrator;
    private final ImageCache imageCache;

    public DockerSandboxExecutor(SandboxOrchestrator orchestrator, ImageCache imageCache) {
        this.orchestrator = orchestrator;
        this.imageCache = imageCache;
    }

    @Override
    public JobResult execute(JobMessage msg) {
        try {
            imageCache.verifyAvailable(msg.image());
        } catch (SandboxImageMissingException e) {
            return new JobResult(msg.token(), Status.BOXERR, null, null, null,
                    null, null, e.getMessage(),
                    null, null, null, hostname(), Instant.now(), msg.traceContext(), false);
        }
        return orchestrator.execute(msg, msg.image(),
                msg.compileCpuTime() != null ? msg.compileCpuTime() : 10.0,
                msg.compileMemory() != null ? msg.compileMemory() : 524288);
    }

    private static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "unknown"; }
    }
}
