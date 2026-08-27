package com.arqsync.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void findByPathReturnsTheMatchingProject() {
        projectRepository.save(new Project("/repo/a", "a", null, LocalDateTime.now()));

        Optional<Project> found = projectRepository.findByPath("/repo/a");

        assertThat(found).isPresent();
        assertThat(found.get().getPath()).isEqualTo("/repo/a");
    }

    @Test
    void findByPathReturnsEmptyWhenNoProjectMatches() {
        Optional<Project> found = projectRepository.findByPath("/repo/does-not-exist");

        assertThat(found).isEmpty();
    }

    @Test
    void savingTwoProjectsWithTheSamePathViolatesTheUniqueConstraint() {
        projectRepository.saveAndFlush(new Project("/repo/dup", "dup", null, LocalDateTime.now()));

        assertThatThrownBy(() ->
                projectRepository.saveAndFlush(new Project("/repo/dup", "dup-again", null, LocalDateTime.now()))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
