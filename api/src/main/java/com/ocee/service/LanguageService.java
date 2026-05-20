package com.ocee.service;

import com.ocee.DTO.LanguageResponse;
import com.ocee.entity.Language;
import com.ocee.exception.LanguageNotFoundException;
import com.ocee.mapper.LanguageMapper;
import com.ocee.repository.LanguageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class LanguageService {
    private final LanguageRepository repo;
    private final LanguageMapper mapper;

    public LanguageService(LanguageRepository repo, LanguageMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    public List<LanguageResponse> listActive() {
        return repo.findAllByIsActiveTrueOrderById().stream().map(mapper::toResponse).toList();
    }

    public LanguageResponse getById(int id) {
        Language l = repo.findById(id).orElseThrow(() -> new LanguageNotFoundException(id));
        if (!Boolean.TRUE.equals(l.getIsActive())) throw new LanguageNotFoundException(id);
        return mapper.toResponse(l);
    }

    public Language requireActive(int id) {
        Language l = repo.findById(id).orElseThrow(() -> new LanguageNotFoundException(id));
        if (!Boolean.TRUE.equals(l.getIsActive())) throw new LanguageNotFoundException(id);
        return l;
    }
}
