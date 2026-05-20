package com.ocee.mapper;

import com.ocee.DTO.*;
import com.ocee.entity.Submission;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = com.ocee.DTO.StatusResponse.class)
public interface SubmissionMapper {

    @Mapping(target = "languageId", source = "language.id")
    @Mapping(target = "status", expression = "java(StatusResponse.from(s.getStatus()))")
    SubmissionResponse toResponse(Submission s);

    @Mapping(target = "languageId", source = "language.id")
    @Mapping(target = "status", expression = "java(StatusResponse.from(s.getStatus()))")
    SubmissionSummaryResponse toSummary(Submission s);
}
