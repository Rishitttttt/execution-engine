package com.ocee.pagination;

import com.ocee.exception.InvalidCursorException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class CursorCodecTest {

    @Test
    void encodeThenDecodeReturnsOriginalValues() {
        Instant when = Instant.parse("2026-05-02T10:00:00Z");
        long id = 12345L;

        String encoded = CursorCodec.encode(when, id);
        CursorCodec.Cursor decoded = CursorCodec.decode(encoded);

        assertThat(decoded.createdAt()).isEqualTo(when);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void decodeRejectsMalformedInput() {
        assertThatThrownBy(() -> CursorCodec.decode("not-base64!!!"))
                .isInstanceOf(InvalidCursorException.class);
        assertThatThrownBy(() -> CursorCodec.decode("Zm9v"))                       // "foo"
                .isInstanceOf(InvalidCursorException.class);
    }
}
