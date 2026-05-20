package com.ocee.exception;
import org.springframework.http.HttpStatus;

public class SubmissionNotFoundException extends ApiException {
    public SubmissionNotFoundException(String token) {
        super("No submission with token=" + token);
    }
    public HttpStatus status() { return HttpStatus.NOT_FOUND; }
    public String typeSlug() { return "submission-not-found"; }
}
