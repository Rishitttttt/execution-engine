package com.ocee.DTO;

public record LanguageResponse(
    Integer id,
    String name,
    String version,
    String sourceFile,
    Double defaultCpuTime,
    Integer defaultMemory,
    Double maxCpuTime,
    Integer maxMemory,
    Integer maxSourceSize,
    Boolean isActive
) {}
