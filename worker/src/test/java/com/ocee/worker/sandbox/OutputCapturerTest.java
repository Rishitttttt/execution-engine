package com.ocee.worker.sandbox;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OutputCapturerTest {
    @Test
    void belowCapKeepsAllBytes() {
        OutputCapturer c = new OutputCapturer(8);
        c.appendStdout("hello".getBytes(), 5);
        assertThat(c.stdout()).isEqualTo("hello");
        assertThat(c.truncated()).isFalse();
    }

    @Test
    void exactCapNotTruncated() {
        OutputCapturer c = new OutputCapturer(5);
        c.appendStdout("abcde".getBytes(), 5);
        assertThat(c.stdout()).isEqualTo("abcde");
        assertThat(c.truncated()).isFalse();
    }

    @Test
    void overCapKeepsHeadAndFlagsTruncated() {
        OutputCapturer c = new OutputCapturer(5);
        c.appendStdout("abcdefghij".getBytes(), 10);
        assertThat(c.stdout()).isEqualTo("abcde");
        assertThat(c.truncated()).isTrue();
    }

    @Test
    void truncationFlagAppliesAcrossStreams() {
        OutputCapturer c = new OutputCapturer(3);
        c.appendStderr("xxxxxx".getBytes(), 6);
        assertThat(c.stderr()).isEqualTo("xxx");
        assertThat(c.truncated()).isTrue();
    }
}
