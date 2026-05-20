package com.ocee.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocee.DTO.*;
import com.ocee.config.JacksonConfig;
import com.ocee.exception.GlobalExceptionHandler;
import com.ocee.exception.LanguageNotFoundException;
import com.ocee.service.SubmissionService;
import com.ocee.wait.WaitRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubmissionController.class)
@Import({JacksonConfig.class, GlobalExceptionHandler.class})
class SubmissionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockBean SubmissionService service;
    @MockBean WaitRegistry waitRegistry;

    @Test
    void post_returns201_andSnakeCaseBody() throws Exception {
        UUID token = UUID.randomUUID();
        SubmissionResponse resp = new SubmissionResponse(
                token, new StatusResponse(1, "In Queue"), 1, "print(1)",
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, Instant.parse("2026-05-02T10:00:00Z"), null);
        when(service.create(any(), any(), any())).thenReturn(resp);

        mvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language_id": 1, "source_code": "print(1)"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(token.toString()))
                .andExpect(jsonPath("$.status.code").value(1))
                .andExpect(jsonPath("$.language_id").value(1));
    }

    @Test
    void post_invalidBody_returnsValidationProblem() throws Exception {
        mvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language_id": null, "source_code": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://ocee.dev/errors/validation"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void get_unknownLanguageReturns404Problem() throws Exception {
        when(service.getByToken(any())).thenThrow(new LanguageNotFoundException(999));
        mvc.perform(get("/api/submissions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://ocee.dev/errors/language-not-found"));
    }
}
