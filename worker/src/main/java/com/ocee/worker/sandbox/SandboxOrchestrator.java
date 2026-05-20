package com.ocee.worker.sandbox;

import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.common.Status;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;

public class SandboxOrchestrator {

    private final VolumeManager vm;
    private final ContainerRunner runner;
    private final String workerId;
    private final int outputCapBytes;
    private final long tmpfsBytes;

    public SandboxOrchestrator(VolumeManager vm, ContainerRunner runner,
                               String workerId, int outputCapBytes, long tmpfsBytes) {
        this.vm = vm;
        this.runner = runner;
        this.workerId = workerId;
        this.outputCapBytes = outputCapBytes;
        this.tmpfsBytes = tmpfsBytes;
    }

    public JobResult execute(JobMessage msg, String image, double compileCpuSec, int compileMemKib) {
        vm.create(msg.token());
        try {
            String volume = VolumeManager.volumeName(msg.token());
            String compile = msg.compileCommand();
            boolean hasCompile = compile != null && !compile.isBlank();

            List<ContainerRunner.FilePayload> sourcePayload =
                    List.of(new ContainerRunner.FilePayload(msg.sourceFile(), msg.sourceCode()));

            if (hasCompile) {
                RunLimits cl = RunLimits.compile(compileCpuSec, compileMemKib, tmpfsBytes);
                RunOutcome co = runner.runOnce(image, compile, volume,
                        false, sourcePayload, null, cl, msg.token(), "compile");
                if (co.dockerError())
                    return result(msg, Status.BOXERR, null, null, co.stderr(), null, null, co);
                if (co.exitCode() != 0)
                    return result(msg, Status.CE, null, co.stderr(), null, co.exitCode(), null, co);
            }

            double runCpuSec = msg.cpuTimeLimit() != null ? msg.cpuTimeLimit() : 2.0;
            int runMemKib = msg.memoryLimit() != null ? msg.memoryLimit() : 131072;
            RunLimits rl = RunLimits.run(runCpuSec, runMemKib, tmpfsBytes);

            // For compile+run: volume already has source from compile stage; mount RO.
            // For run-only: source needs to land first; mount RW.
            boolean readOnlyVolume = hasCompile;
            List<ContainerRunner.FilePayload> runFiles = hasCompile ? null : sourcePayload;

            RunOutcome ro = runner.runOnce(image, msg.runCommand(), volume,
                    readOnlyVolume, runFiles, msg.stdIn(), rl, msg.token(), "run");

            return mapRun(msg, ro);
        } finally {
            try { vm.remove(msg.token()); } catch (Exception ignored) {}
        }
    }

    private JobResult mapRun(JobMessage msg, RunOutcome o) {
        if (o.dockerError())  return result(msg, Status.BOXERR, null, null, o.stderr(), null, null, o);
        if (o.timedOut())     return result(msg, Status.TLE, o.stdout(), null, o.stderr(), null, null, o);
        if (o.oomKilled())    return result(msg, Status.MLE, o.stdout(), null, o.stderr(), o.exitCode(), null, o);
        if (o.signal() != null)
            return result(msg, Status.findRuntimeErrorBySignal(o.signal()),
                    o.stdout(), null, o.stderr(), o.exitCode(), o.signal(), o);
        if (o.exitCode() == 0) return result(msg, Status.AC, o.stdout(), null, o.stderr(), 0, null, o);
        return result(msg, Status.NZEC, o.stdout(), null, o.stderr(), o.exitCode(), null, o);
    }

    private JobResult result(JobMessage msg, Status status, String stdout, String compileOutput,
                             String stderr, Integer exit, Integer signal, RunOutcome o) {
        Double cpuTime = (o.userCpuSeconds() != null && o.sysCpuSeconds() != null)
                ? o.userCpuSeconds() + o.sysCpuSeconds()
                : o.wallClockSeconds();
        Integer memKib;
        if (o.maxRssKib() != null) memKib = o.maxRssKib().intValue();
        else if (o.memoryKib() != null) memKib = o.memoryKib().intValue();
        else memKib = null;
        return new JobResult(
                msg.token(), status, stdout, stderr, compileOutput,
                exit, signal, null,
                cpuTime, o.wallClockSeconds(), memKib,
                hostname(), Instant.now(), msg.traceContext(),
                o.outputTruncated());
    }

    private static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); } catch (Exception e) { return "unknown"; }
    }
}
