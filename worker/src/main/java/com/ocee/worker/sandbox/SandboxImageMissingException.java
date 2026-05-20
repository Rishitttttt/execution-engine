package com.ocee.worker.sandbox;

public class SandboxImageMissingException extends RuntimeException {
    public SandboxImageMissingException(String image) {
        super("sandbox image not built on this worker: " + image);
    }
}
