package com.ocee.controller;

import com.ocee.exception.GlobalExceptionHandler;
import com.ocee.exception.LanguageNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void apiExceptionProducesProblemDetailWithStableType() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getRequestURI()).thenReturn("/api/languages/999");

        ProblemDetail pd = handler.handleApi(new LanguageNotFoundException(999), req);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getType().toString()).isEqualTo("https://ocee.dev/errors/language-not-found");
        assertThat(pd.getDetail()).contains("999");
        assertThat(pd.getInstance().toString()).isEqualTo("/api/languages/999");
    }
}
