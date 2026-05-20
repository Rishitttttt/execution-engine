package com.ocee.worker.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ContainerRunner {

    public record FilePayload(String name, String content) {}

    private static final String METRICS_MARKER = "__OCEE_METRICS__";

    private final DockerClient docker;
    private final String workerId;
    private final int outputCapBytes;

    public ContainerRunner(DockerClient docker, String workerId, int outputCapBytes) {
        this.docker = docker;
        this.workerId = workerId;
        this.outputCapBytes = outputCapBytes;
    }

    public RunOutcome runOnce(String image, String command, String volumeName,
                              boolean readOnlyVolume, List<FilePayload> filesBeforeRun,
                              String stdinContent, RunLimits limits, String token, String stage) {
        long startNs = System.nanoTime();
        OutputCapturer cap = new OutputCapturer(outputCapBytes);

        HostConfig hc = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withReadonlyRootfs(true)
                .withTmpFs(java.util.Map.of("/tmp",
                        "rw,nosuid,nodev,size=" + limits.tmpfsBytes()))
                .withMemory(limits.memoryKib() * 1024L)
                .withMemorySwap(limits.memoryKib() * 1024L)
                .withCpuCount(1L)
                .withPidsLimit(Long.valueOf(limits.pidsLimit()))
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(java.util.List.of("no-new-privileges:true"))
                .withInit(true)
                .withAutoRemove(false)
                .withBinds(new Bind(volumeName,
                        new Volume("/work"),
                        readOnlyVolume ? AccessMode.ro : AccessMode.rw))
                .withUlimits(new Ulimit[]{ new Ulimit("nofile", (long) limits.nofile(), (long) limits.nofile()) });

        String wrapped = "/usr/bin/time -f '" + METRICS_MARKER + "%M %U %S %e" + METRICS_MARKER
                + "' sh -c " + shellQuote(command);

        var create = docker.createContainerCmd(image)
                .withHostConfig(hc)
                .withWorkingDir("/work")
                .withUser("1000:1000")
                .withEntrypoint("/bin/sh", "-c")
                .withCmd(wrapped)
                .withStdinOpen(stdinContent != null)
                .withAttachStdin(stdinContent != null)
                .withLabels(SandboxLabels.forResource(token, workerId, stage))
                .exec();

        String id = create.getId();

        try {
            if (filesBeforeRun != null && !filesBeforeRun.isEmpty()) {
                for (FilePayload p : filesBeforeRun) {
                    byte[] tar = TarUtil.singleFile(p.name(), p.content());
                    docker.copyArchiveToContainerCmd(id)
                            .withRemotePath("/work")
                            .withTarInputStream(new ByteArrayInputStream(tar))
                            .exec();
                }
            }

            docker.startContainerCmd(id).exec();

            if (stdinContent != null) {
                docker.attachContainerCmd(id)
                        .withStdIn(new ByteArrayInputStream(stdinContent.getBytes(StandardCharsets.UTF_8)))
                        .withFollowStream(false).exec(new ResultCallback.Adapter<>()).awaitCompletion(2, TimeUnit.SECONDS);
            }

            CountDownLatch logsDone = new CountDownLatch(1);
            docker.logContainerCmd(id)
                    .withStdOut(true).withStdErr(true)
                    .withFollowStream(true).withTailAll()
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override public void onNext(Frame f) {
                            byte[] b = f.getPayload();
                            if (b == null) return;
                            if (f.getStreamType() == StreamType.STDERR) cap.appendStderr(b, b.length);
                            else cap.appendStdout(b, b.length);
                        }
                        @Override public void onComplete() { logsDone.countDown(); super.onComplete(); }
                        @Override public void onError(Throwable t) { logsDone.countDown(); super.onError(t); }
                    });

            boolean finishedInTime = waitContainer(id, limits.wallClock());
            if (!finishedInTime) {
                try { docker.killContainerCmd(id).exec(); } catch (Exception ignored) {}
            }
            logsDone.await(2, TimeUnit.SECONDS);

            InspectContainerResponse insp = docker.inspectContainerCmd(id).exec();
            int exit = insp.getState().getExitCodeLong() == null ? -1 : insp.getState().getExitCodeLong().intValue();
            Boolean oom = insp.getState().getOOMKilled();

            String rawStderr = cap.stderr();
            MetricsParser.Result m = extractMetrics(rawStderr);
            String cleanStderr = stripMetricsBlock(rawStderr);

            Integer signal = signalFromExit(exit);
            double wallSec = (System.nanoTime() - startNs) / 1_000_000_000.0;

            return new RunOutcome(
                    exit, signal,
                    Boolean.TRUE.equals(oom),
                    !finishedInTime,
                    false,
                    cap.stdout(), cleanStderr, cap.truncated(),
                    m.wallSeconds() != null ? m.wallSeconds() : wallSec,
                    m.maxRssKib(),
                    null,
                    m.userCpuSeconds(), m.sysCpuSeconds(), m.maxRssKib());
        } catch (NotFoundException nfe) {
            return new RunOutcome(-1, null, false, false, true, "", nfe.getMessage(), false,
                    (System.nanoTime() - startNs) / 1_000_000_000.0, null, "image-or-container missing",
                    null, null, null);
        } catch (Exception e) {
            return new RunOutcome(-1, null, false, false, true, "", e.getMessage(), false,
                    (System.nanoTime() - startNs) / 1_000_000_000.0, null, e.getClass().getSimpleName(),
                    null, null, null);
        } finally {
            try { docker.removeContainerCmd(id).withForce(true).exec(); } catch (Exception ignored) {}
        }
    }

    private boolean waitContainer(String id, Duration wall) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        docker.waitContainerCmd(id).exec(new ResultCallback.Adapter<>() {
            @Override public void onNext(com.github.dockerjava.api.model.WaitResponse r) { done.countDown(); }
        });
        return done.await(wall.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static Integer signalFromExit(int exit) {
        if (exit > 128 && exit < 128 + 64) return exit - 128;
        return null;
    }

    private static MetricsParser.Result extractMetrics(String stderr) {
        if (stderr == null || stderr.isEmpty()) return MetricsParser.EMPTY;
        int end = stderr.lastIndexOf(METRICS_MARKER);
        if (end <= 0) return MetricsParser.EMPTY;
        int start = stderr.lastIndexOf(METRICS_MARKER, end - 1);
        if (start < 0 || start == end) return MetricsParser.EMPTY;
        return MetricsParser.parse(stderr.substring(start + METRICS_MARKER.length(), end));
    }

    private static String stripMetricsBlock(String stderr) {
        if (stderr == null || stderr.isEmpty()) return stderr;
        int end = stderr.lastIndexOf(METRICS_MARKER);
        if (end <= 0) return stderr;
        int start = stderr.lastIndexOf(METRICS_MARKER, end - 1);
        if (start < 0 || start == end) return stderr;
        int blockStart = stderr.lastIndexOf('\n', start - 1) + 1;
        String prevLine = stderr.substring(blockStart, start);
        if (!(prevLine.startsWith("Command exited") || prevLine.startsWith("Command terminated") || prevLine.isEmpty())) {
            blockStart = start;
        }
        int blockEnd = end + METRICS_MARKER.length();
        if (blockEnd < stderr.length() && stderr.charAt(blockEnd) == '\n') blockEnd++;
        return stderr.substring(0, blockStart) + stderr.substring(blockEnd);
    }

    private static String shellQuote(String s) { return "'" + s.replace("'", "'\\''") + "'"; }
}
