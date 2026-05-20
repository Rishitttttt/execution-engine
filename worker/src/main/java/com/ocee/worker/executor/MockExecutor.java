package com.ocee.worker.executor;

import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;
import com.ocee.common.Status;
import com.ocee.worker.config.WorkerProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile("mock-executor")
public class MockExecutor implements Executor {

    private static final List<Status> OUTCOMES = List.of(Status.AC, Status.WA, Status.TLE, Status.SIGSEGV);

    private final WorkerProperties props;

    public MockExecutor(WorkerProperties props) { this.props = props; }

    @Override
    public JobResult execute(JobMessage msg) {
        long min = props.mockMinMs(), max = props.mockMaxMs();
        long sleep = ThreadLocalRandom.current().nextLong(min, Math.max(min + 1, max + 1));
        try { Thread.sleep(sleep); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JobResult(msg.token(), Status.BOXERR, null, "interrupted",
                    null, null, null, "worker interrupted", null, null, null, hostname(),
                    Instant.now(), msg.traceContext(), false);
        }
        Status status = OUTCOMES.get(ThreadLocalRandom.current().nextInt(OUTCOMES.size()));
        String stdOut = status == Status.AC ? "ok\n" : null;
        Double time = sleep / 1000.0;
        return new JobResult(msg.token(), status, stdOut, null, null,
                status == Status.AC ? 0 : 1, null, null,
                time, time, 1024, hostname(), Instant.now(), msg.traceContext(), false);
    }

    private static String hostname() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }
}
