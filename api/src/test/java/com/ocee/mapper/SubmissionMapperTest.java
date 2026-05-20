package com.ocee.mapper;

import com.ocee.common.Status;
import com.ocee.DTO.SubmissionResponse;
import com.ocee.entity.Language;
import com.ocee.entity.Submission;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionMapperTest {

    private final SubmissionMapper mapper = Mappers.getMapper(SubmissionMapper.class);

    @Test
    void mapsCoreFieldsAndStatus() {
        Language lang = new Language(); lang.setId(1);
        Submission s = new Submission();
        s.setToken(UUID.randomUUID()); s.setLanguage(lang);
        s.setStatus(Status.QUEUED); s.setSourceCode("print(1)");

        SubmissionResponse r = mapper.toResponse(s);

        assertThat(r.token()).isEqualTo(s.getToken());
        assertThat(r.languageId()).isEqualTo(1);
        assertThat(r.status().code()).isEqualTo(Status.QUEUED.getCode());
        assertThat(r.sourceCode()).isEqualTo("print(1)");
    }
}
