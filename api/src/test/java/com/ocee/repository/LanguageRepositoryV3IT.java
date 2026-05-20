package com.ocee.repository;

import com.ocee.TestcontainersConfiguration;
import com.ocee.entity.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LanguageRepositoryV3IT {
    @Autowired LanguageRepository repo;

    @Test
    void newColumnsLoadFromExistingSeedRows() {
        Optional<Language> py = repo.findById(1);
        assertThat(py).isPresent();
        assertThat(py.get().getCompileCpuTime()).isEqualTo(10.0);
        assertThat(py.get().getCompileMemory()).isEqualTo(524288);
    }
}
