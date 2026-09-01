package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.ArchitectureStyle;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates the real Flyway migration (V1__initial_schema.sql) against a real
 * PostgreSQL instance, with Hibernate's ddl-auto=validate confirming the
 * mapped entities match it exactly (SPEC-persistence.md, section 6) — the
 * check H2 alone can't fully guarantee.
 *
 * Named *IT (not *Test) per SPEC-testing.md's Surefire/Failsafe convention:
 * runs under `./mvnw verify`, not `./mvnw test`, since it requires Docker.
 */
@Testcontainers
@SpringBootTest
class PersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Test
    void springContextStartsWithDdlAutoValidateAgainstTheRealFlywaySchema() {
        // If the entities didn't match the Flyway-migrated schema, the context
        // would have failed to start before this test method even ran.
        assertThat(projectRepository).isNotNull();
    }

    @Test
    void savesAFullAnalysisAgainstRealPostgres() {
        ProjectScan projectScan = new ProjectScan("/repo/integration-test", List.of(), List.of());
        AnalysisResult analysisResult = new AnalysisResult(
                new DependencyGraph(Set.of(), List.of()),
                List.of(),
                List.of(),
                List.of(),
                new AnalysisMetrics(0, 0, 0, 0, List.of()),
                new ArchitectureStyle("Não identificado", "")
        );

        persistenceService.save(projectScan, analysisResult);

        assertThat(projectRepository.findByPath("/repo/integration-test")).isPresent();
        assertThat(analysisRepository.findAll()).hasSize(1);
    }
}
