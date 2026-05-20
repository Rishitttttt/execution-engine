package com.ocee.worker.sandbox;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MetricsParserTest {
    @Test void wellFormed() {
        var r = MetricsParser.parse("1024 0.12 0.04 0.20\n");
        assertThat(r.maxRssKib()).isEqualTo(1024L);
        assertThat(r.userCpuSeconds()).isEqualTo(0.12);
        assertThat(r.sysCpuSeconds()).isEqualTo(0.04);
        assertThat(r.wallSeconds()).isEqualTo(0.20);
    }
    @Test void tolerantOnGarbage() {
        var r = MetricsParser.parse("garbage\n");
        assertThat(r.maxRssKib()).isNull();
        assertThat(r.userCpuSeconds()).isNull();
    }
    @Test void tolerantOnEmpty() {
        var r = MetricsParser.parse("");
        assertThat(r.maxRssKib()).isNull();
    }
    @Test void localeWithCommaDecimalsLeavesDoublesNull() {
        var r = MetricsParser.parse("2048 0,30 0,10 0,40");
        assertThat(r.maxRssKib()).isEqualTo(2048L);
        assertThat(r.userCpuSeconds()).isNull();
    }
}
