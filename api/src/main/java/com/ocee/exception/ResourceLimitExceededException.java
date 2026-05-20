package com.ocee.exception;
import org.springframework.http.HttpStatus;

public class ResourceLimitExceededException extends ApiException {
    public ResourceLimitExceededException(String field, Number requested, Number max) {
        super("%s=%s exceeds language max=%s".formatted(field, requested, max));
    }
    public HttpStatus status() { return HttpStatus.UNPROCESSABLE_ENTITY; }
    public String typeSlug() { return "resource-limit-exceeded"; }
}
