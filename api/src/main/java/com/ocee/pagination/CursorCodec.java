package com.ocee.pagination;

import com.ocee.exception.InvalidCursorException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public final class CursorCodec {

    public record Cursor(Instant createdAt, long id) {}

    private CursorCodec() {}

    public static String encode(Instant createdAt, long id) {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep <= 0) throw new IllegalArgumentException("missing separator");
            Instant when = Instant.parse(raw.substring(0, sep));
            long id = Long.parseLong(raw.substring(sep + 1));
            return new Cursor(when, id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursorException("Invalid cursor: " + cursor, e);
        }
    }
}
