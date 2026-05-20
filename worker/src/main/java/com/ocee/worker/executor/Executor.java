package com.ocee.worker.executor;

import com.ocee.common.JobMessage;
import com.ocee.common.JobResult;

public interface Executor {
    JobResult execute(JobMessage message);
}
