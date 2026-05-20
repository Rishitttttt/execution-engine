package com.ocee.mapper;

import com.ocee.DTO.LanguageResponse;
import com.ocee.entity.Language;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LanguageMapper {
    LanguageResponse toResponse(Language l);
}
