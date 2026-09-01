package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.ArchitectureStyle;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.scanner.ProjectScan;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(PersistenceServiceConfiguration.class)
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
                List.of(),
                new AnalysisMetrics(0, 0, 0, 0, List.of()),
                new ArchitectureStyle("Não identificado", "")
        );
    }

    @Test
    void firstScanOfANewProjectCreatesProjectAndAnalysis() {
        persistenceService.save(projectScan("/repo/new-project"), emptyAnalysisResult());

        assertThat(projectRepository.findByPath("/repo/new-project")).isPresent();
        assertThat(analysisRepository.findAll()).hasSize(1);
    }

    @Test
    // @DataJpaTest wraps each test in its own ambient transaction by default, which
    // keeps a Hibernate Session open throughout the test regardless of whether the
    // production code's own @Transactional boundaries actually engage - masking a
    // real self-invocation bug this exact scenario used to trigger (see
    // PersistenceWriter's Javadoc). Suspending it here makes this test exercise the
    // real per-call transaction boundaries, like a real run does.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void secondScanOfTheSamePathReusesTheProjectAndAddsANewAnalysis() {
        persistenceService.save(projectScan("/repo/same-path"), emptyAnalysisResult());
        Long firstProjectId = projectRepository.findByPath("/repo/same-path").orElseThrow().getId();

        persistenceService.save(projectScan("/repo/same-path"), emptyAnalysisResult());
        Long secondProjectId = projectRepository.findByPath("/repo/same-path").orElseThrow().getId();

        assertThat(secondProjectId).isEqualTo(firstProjectId);
        assertThat(analysisRepository.findAll()).hasSize(2);

        // Unlike every other test in this class, this one suspends the ambient test
        // transaction (above), so its writes are real commits, not auto-rolled-back -
        // clean up explicitly so they don't leak into other tests sharing this H2
        // instance (Surefire reuses one JVM/one in-memory database across test classes
        // by default).
        analysisRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void saveNeverThrowsWhenTheDatabaseIsUnavailable(@Autowired javax.sql.DataSource dataSource) throws SQLException {
        ((HikariDataSource) dataSource).close();

        assertThatCode(() -> persistenceService.save(projectScan("/repo/db-down"), emptyAnalysisResult()))
                .doesNotThrowAnyException();
    }
}
