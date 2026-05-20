package com.ocee.entity;

import com.ocee.common.Status;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class StatusConverter implements AttributeConverter<Status, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Status status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public Status convertToEntityAttribute(Integer code) {
        return code == null ? null : Status.fromCode(code);
    }
}
