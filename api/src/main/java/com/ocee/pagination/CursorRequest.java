package com.ocee.pagination;

public record CursorRequest(String cursor, int limit) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public static CursorRequest of(String cursor, Integer limit) {
        int effective = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        return new CursorRequest(cursor, effective);
    }
}
