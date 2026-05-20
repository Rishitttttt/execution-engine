package com.ocee.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {
    @Test void exactMatch() {
        assertThat(OutputComparator.matches("hello\n", "hello\n")).isTrue();
    }
    @Test void missingTrailingNewlineStillMatches() {
        assertThat(OutputComparator.matches("hello", "hello\n")).isTrue();
    }
    @Test void trailingWhitespacePerLineIgnored() {
        assertThat(OutputComparator.matches("a   \nb\n", "a\nb\n")).isTrue();
    }
    @Test void interiorBlankLinesPreserved() {
        assertThat(OutputComparator.matches("a\n\nb\n", "a\nb\n")).isFalse();
    }
    @Test void differentContentDoesNotMatch() {
        assertThat(OutputComparator.matches("hello\n", "world\n")).isFalse();
    }
    @Test void emptyVsEmpty() {
        assertThat(OutputComparator.matches("", "")).isTrue();
        assertThat(OutputComparator.matches("\n", "")).isTrue();
    }
    @Test void nullActualDoesNotMatchNonNullExpected() {
        assertThat(OutputComparator.matches(null, "x")).isFalse();
    }
}
