package com.ocee.worker.sandbox;

import java.util.Map;

public final class SandboxLabels {
    public static final String ROLE      = "ocee.role";
    public static final String TOKEN     = "ocee.submission_token";
    public static final String WORKER_ID = "ocee.worker_id";
    public static final String STAGE     = "ocee.stage";
    public static final String ROLE_VALUE = "sandbox";

    private SandboxLabels() {}

    public static Map<String, String> forResource(String token, String workerId, String stage) {
        return Map.of(ROLE, ROLE_VALUE, TOKEN, token, WORKER_ID, workerId, STAGE, stage);
    }
}
