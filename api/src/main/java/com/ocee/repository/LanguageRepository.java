package com.ocee.repository;

import com.ocee.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LanguageRepository extends JpaRepository<Language, Integer> {
    List<Language> findAllByIsActiveTrueOrderById();
}
