package com.ocee.exception;
import org.springframework.http.HttpStatus;

public class LanguageNotFoundException extends ApiException {
    public LanguageNotFoundException(int languageId) {
        super("No language with id=" + languageId);
    }
    public HttpStatus status() { return HttpStatus.NOT_FOUND; }
    public String typeSlug() { return "language-not-found"; }
}
