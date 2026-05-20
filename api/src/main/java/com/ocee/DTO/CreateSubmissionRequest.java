package com.ocee.DTO;

import jakarta.validation.constraints.*;

public record CreateSubmissionRequest(
    @NotNull
    Integer languageId,

    @NotBlank
    @Size(max = 65536, message = "source_code exceeds 65536 bytes")
    String sourceCode,

    @Size(max = 65536) String stdIn,
    @Size(max = 65536) String expectedOutput,

    @DecimalMin(value = "0.1") Double cpuTimeLimit,
    @DecimalMin(value = "0.1") Double cpuExtraTimeLimit,
    @DecimalMin(value = "0.1") Double wallTimeLimit,
    @Min(1024)                 Integer memoryLimit,
    @Min(1024)                 Integer stackLimit,
    @Min(1)                    Integer maxProcessesAndOrThreadsLimit,
    @Min(1)                    Integer maxFileSizeLimit,

    @Size(max = 2048)
    @Pattern(regexp = "^https?://.*", message = "callback_url must be http or https")
    String callbackUrl
) {}
