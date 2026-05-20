package com.ocee.controller;

import com.ocee.DTO.LanguageResponse;
import com.ocee.service.LanguageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/languages")
public class LanguageController {

    private final LanguageService service;

    public LanguageController(LanguageService service) { this.service = service; }

    @GetMapping
    public List<LanguageResponse> list() { return service.listActive(); }

    @GetMapping("/{id}")
    public LanguageResponse get(@PathVariable int id) { return service.getById(id); }
}
