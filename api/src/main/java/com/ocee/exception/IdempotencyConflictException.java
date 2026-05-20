package com.ocee.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends ApiException {
    public IdempotencyConflictException(String key) {
        super("Key '" + key + "' was previously used with a different request body");
    }
    @Override public HttpStatus status() { return HttpStatus.CONFLICT; }
    @Override public String typeSlug() { return "idempotency-conflict"; }
}
