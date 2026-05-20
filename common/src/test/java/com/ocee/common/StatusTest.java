package com.ocee.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StatusTest {
    @Test
    void fromCode_returnsMatchingStatusForEveryDeclaredValue() {
        for (Status s : Status.values()) {
            assertThat(Status.fromCode(s.getCode())).isEqualTo(s);
        }
    }

    @Test
    void fromCode_throwsOnUnknownCode() {
        assertThatThrownBy(() -> Status.fromCode(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    void findRuntimeErrorBySignal_mapsKnownSignalsAndDefaultsToOther() {
        assertThat(Status.findRuntimeErrorBySignal(11)).isEqualTo(Status.SIGSEGV);
        assertThat(Status.findRuntimeErrorBySignal(8)).isEqualTo(Status.SIGFPE);
        assertThat(Status.findRuntimeErrorBySignal(null)).isEqualTo(Status.OTHER);
        assertThat(Status.findRuntimeErrorBySignal(99)).isEqualTo(Status.OTHER);
    }
}
