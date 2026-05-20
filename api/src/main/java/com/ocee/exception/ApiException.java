package com.ocee.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

public abstract class ApiException extends RuntimeException {
    private static final String BASE = "https://ocee.dev/errors/";

    protected ApiException(String message) { super(message); }
    protected ApiException(String message, Throwable cause) { super(message, cause); }

    public abstract HttpStatus status();
    public abstract String typeSlug();

    public ProblemDetail toProblemDetail() {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status(), getMessage());
        pd.setType(URI.create(BASE + typeSlug()));
        return pd;
    }
}
