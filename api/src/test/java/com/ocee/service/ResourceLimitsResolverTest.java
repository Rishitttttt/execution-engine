package com.ocee.service;

import com.ocee.DTO.CreateSubmissionRequest;
import com.ocee.entity.Language;
import com.ocee.entity.Submission;
import com.ocee.exception.ResourceLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ResourceLimitsResolverTest {

    private final ResourceLimitsResolver resolver = new ResourceLimitsResolver();

    private Language python() {
        Language l = new Language();
        l.setId(1); l.setDefaultCpuTime(2.0); l.setMaxCpuTime(10.0);
        l.setDefaultMemory(128_000); l.setMaxMemory(256_000); l.setMaxSourceSize(65536);
        return l;
    }

    @Test
    void appliesLanguageDefaultsWhenRequestOmitsLimits() {
        CreateSubmissionRequest req = new CreateSubmissionRequest(1, "print(1)", null, null,
                null, null, null, null, null, null, null, null);
        Submission s = new Submission();
        resolver.apply(req, python(), s);
        assertThat(s.getCpuTimeLimit()).isEqualTo(2.0);
        assertThat(s.getMemoryLimit()).isEqualTo(128_000);
    }

    @Test
    void throwsWhenOverrideExceedsLanguageMax() {
        CreateSubmissionRequest req = new CreateSubmissionRequest(1, "x", null, null,
                99.0, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> resolver.apply(req, python(), new Submission()))
                .isInstanceOf(ResourceLimitExceededException.class)
                .hasMessageContaining("cpu_time_limit");
    }
}
