package com.ocee.service;

import com.ocee.DTO.CreateSubmissionRequest;
import com.ocee.entity.Language;
import com.ocee.entity.Submission;
import com.ocee.exception.ResourceLimitExceededException;
import org.springframework.stereotype.Component;

@Component
public class ResourceLimitsResolver {

    public void apply(CreateSubmissionRequest req, Language lang, Submission sub) {
        Double cpu = req.cpuTimeLimit() != null ? req.cpuTimeLimit() : lang.getDefaultCpuTime();
        if (cpu > lang.getMaxCpuTime()) {
            throw new ResourceLimitExceededException("cpu_time_limit", cpu, lang.getMaxCpuTime());
        }
        Integer mem = req.memoryLimit() != null ? req.memoryLimit() : lang.getDefaultMemory();
        if (mem > lang.getMaxMemory()) {
            throw new ResourceLimitExceededException("memory_limit", mem, lang.getMaxMemory());
        }
        if (req.sourceCode() != null && req.sourceCode().length() > lang.getMaxSourceSize()) {
            throw new ResourceLimitExceededException("source_code", req.sourceCode().length(), lang.getMaxSourceSize());
        }
        sub.setCpuTimeLimit(cpu);
        sub.setCpuExtraTimeLimit(req.cpuExtraTimeLimit());
        sub.setWallTimeLimit(req.wallTimeLimit());
        sub.setMemoryLimit(mem);
        sub.setStackLimit(req.stackLimit());
        sub.setMaxProcessesAndOrThreadsLimit(req.maxProcessesAndOrThreadsLimit());
        sub.setMaxFileSizeLimit(req.maxFileSizeLimit());
    }
}
