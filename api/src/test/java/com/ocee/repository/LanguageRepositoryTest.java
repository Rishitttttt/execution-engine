package com.ocee.repository;

import com.ocee.TestcontainersConfiguration;
import com.ocee.entity.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(TestcontainersConfiguration.class)
class LanguageRepositoryTest {

    @Autowired LanguageRepository repo;

    @Test
    void seededLanguagesAreLoadedByFlyway() {
        List<Language> all = repo.findAllByIsActiveTrueOrderById();
        assertThat(all).extracting(Language::getName)
                .containsExactly("python3", "c-gcc", "cpp-gcc", "java-openjdk", "node");
    }

    @Test
    void languageHasExpectedDefaults() {
        Language python = repo.findById(1).orElseThrow();
        assertThat(python.getDefaultCpuTime()).isEqualTo(2.0);
        assertThat(python.getDefaultMemory()).isEqualTo(128000);
        assertThat(python.getMaxSourceSize()).isEqualTo(65536);
        assertThat(python.getRunCommand()).isEqualTo("python3 main.py");
    }
}
