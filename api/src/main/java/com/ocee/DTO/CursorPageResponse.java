package com.ocee.DTO;

import java.util.List;

public record CursorPageResponse<T>(List<T> data, String nextCursor) {}
