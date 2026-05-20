package com.ocee.DTO;

import com.ocee.common.Status;

public record StatusResponse(int code, String description) {
    public static StatusResponse from(Status s) {
        return new StatusResponse(s.getCode(), s.getDescription());
    }
}
