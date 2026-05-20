package com.ocee.service;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {
    @Test
    void producesValidUuidV7() {
        UUID t = new TokenGenerator().newToken();
        assertThat(t.version()).isEqualTo(7);
    }
}
