package com.ocee.queue;

import com.ocee.common.JobMessage;

public interface JobPublisher {
    /** @return the Redis stream record id (XADD return value) */
    String publish(JobMessage message);
}
