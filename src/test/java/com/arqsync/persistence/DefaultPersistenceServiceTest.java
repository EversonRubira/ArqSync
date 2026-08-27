package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.scanner.ProjectScan;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(DefaultPersistenceService.class)
class DefaultPersistenceServiceTest {

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    private ProjectScan projectScan(String rootPath) {
        return new ProjectScan(rootPath, List.of(), List.of());
    }

    private AnalysisResult emptyAnalysisResult() {
        return new AnalysisResult(
                new DependencyGraph(Set.of(), List.of()),
                List.of(),
                List.of(),
                new AnalysisMetrics(0, 0, 0, 0, List.of())
        );
    }

    @Test
    void firstScanOfANewProjectCreatesProjectAndAnalysis() {
        persistenceService.save(projectScan("/repo/new-project"), emptyAnalysisResult());

        assertThat(projectRepository.findByPath("/repo/new-project")).isPresent();
        assertThat(analysisRepository.findAll()).hasSize(1);
    }

    @Test
    void secondScanOfTheSamePathReusesTheProjectAndAddsANewAnalysis() {
        persistenceService.save(projectScan("/repo/same-path"), emptyAnalysisResult());
        Long firstProjectId = projectRepository.findByPath("/repo/same-path").orElseThrow().getId();

        persistenceService.save(projectScan("/repo/same-path"), emptyAnalysisResult());
        Long secondProjectId = projectRepository.findByPath("/repo/same-path").orElseThrow().getId();

        assertThat(secondProjectId).isEqualTo(firstProjectId);
        assertThat(analysisRepository.findAll()).hasSize(2);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void saveNeverThrowsWhenTheDatabaseIsUnavailable(@Autowired javax.sql.DataSource dataSource) throws SQLException {
        ((HikariDataSource) dataSource).close();

        assertThatCode(() -> persistenceService.save(projectScan("/repo/db-down"), emptyAnalysisResult()))
                .doesNotThrowAnyException();
    }
}
