package com.ocee.queue;

import com.ocee.common.JobResult;
import com.ocee.common.Status;
import com.ocee.entity.Submission;
import com.ocee.mapper.SubmissionMapper;
import com.ocee.repository.SubmissionRepository;
import com.ocee.service.OutputComparator;
import com.ocee.wait.WaitRegistry;
import com.ocee.webhook.WebhookEnqueuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class ResultApplier {

    private static final Logger log = LoggerFactory.getLogger(ResultApplier.class);
    private static final Set<Status> TERMINAL = EnumSet.of(
            Status.AC, Status.WA, Status.TLE, Status.CE,
            Status.SIGSEGV, Status.SIGXFSZ, Status.SIGFPE, Status.SIGABRT,
            Status.NZEC, Status.OTHER, Status.BOXERR, Status.EXEERR, Status.MLE);

    private final SubmissionRepository submissions;
    private final SubmissionMapper submissionMapper;
    private final WaitRegistry waitRegistry;
    private final WebhookEnqueuer webhookEnqueuer;

    public ResultApplier(SubmissionRepository submissions,
                         SubmissionMapper submissionMapper,
                         WaitRegistry waitRegistry,
                         WebhookEnqueuer webhookEnqueuer) {
        this.submissions = submissions;
        this.submissionMapper = submissionMapper;
        this.waitRegistry = waitRegistry;
        this.webhookEnqueuer = webhookEnqueuer;
    }

    @Transactional
    public void apply(JobResult result) {
        UUID token = UUID.fromString(result.token());
        Submission s = submissions.findByToken(token).orElse(null);
        if (s == null) {
            log.warn("Result for unknown token {} — discarding", token);
            return;
        }
        if (TERMINAL.contains(s.getStatus())) {
            log.debug("Submission {} already terminal — ignoring duplicate result", token);
            return;
        }
        Status finalStatus = result.status();
        if (finalStatus == Status.AC && s.getExpectedOutput() != null
                && !OutputComparator.matches(result.stdOut(), s.getExpectedOutput())) {
            finalStatus = Status.WA;
        }
        s.setStatus(finalStatus);
        s.setStdOut(result.stdOut());
        s.setStdErr(result.stdErr());
        s.setCompileOutput(result.compileOutput());
        s.setExitCode(result.exitCode());
        s.setExitSignal(result.exitSignal());
        s.setMessage(result.message());
        s.setTime(result.time());
        s.setWallTime(result.wallTime());
        s.setMemory(result.memory());
        s.setExecutionHost(result.executionHost());
        s.setFinishedAt(result.finishedAt());

        webhookEnqueuer.enqueue(s);
        waitRegistry.complete(token, submissionMapper.toResponse(s));
    }
}
