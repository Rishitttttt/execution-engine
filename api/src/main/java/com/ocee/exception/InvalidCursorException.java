package com.ocee.exception;
import org.springframework.http.HttpStatus;

public class InvalidCursorException extends ApiException {
    public InvalidCursorException(String message, Throwable cause) { super(message, cause); }
    public HttpStatus status() { return HttpStatus.BAD_REQUEST; }
    public String typeSlug() { return "invalid-cursor"; }
}
