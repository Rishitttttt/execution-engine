package com.ocee.repository;

import com.ocee.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LanguageRepositoryV4IT {
    @Autowired LanguageRepository repo;

    @Test
    void pythonAndJavaHaveImagesAndAreActive() {
        var py = repo.findById(1).orElseThrow();
        assertThat(py.getImage()).isEqualTo("ocee/sandbox-python:3.11");
        assertThat(py.getIsActive()).isTrue();

        var java = repo.findById(4).orElseThrow();
        assertThat(java.getImage()).isEqualTo("ocee/sandbox-java:21");
        assertThat(java.getIsActive()).isTrue();
    }

    @Test
    void allSeededLanguagesActiveAfterV6() {
        assertThat(repo.findById(2).orElseThrow().getIsActive()).isTrue();
        assertThat(repo.findById(3).orElseThrow().getIsActive()).isTrue();
        assertThat(repo.findById(5).orElseThrow().getIsActive()).isTrue();
        assertThat(repo.findById(2).orElseThrow().getImage()).isEqualTo("ocee/sandbox-c:13");
        assertThat(repo.findById(3).orElseThrow().getImage()).isEqualTo("ocee/sandbox-cpp:13");
        assertThat(repo.findById(5).orElseThrow().getImage()).isEqualTo("ocee/sandbox-node:20");
    }
}
