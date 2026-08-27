package com.arqsync.cli;

import com.arqsync.analyzer.DependencyAnalyzer;
import com.arqsync.exporter.ReportExporter;
import com.arqsync.persistence.DefaultPersistenceService;
import com.arqsync.persistence.PersistenceService;
import com.arqsync.scanner.ScannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the Spring context wires ArqSyncPipelineRunner with real beans for
 * all five dependencies (SPEC-cli.md, seção 5, último caso de teste). Runs
 * against H2 (src/test/resources/application.yml), like every other
 * @SpringBootTest/@DataJpaTest in this project - no real Postgres required.
 *
 * <p>{@code ProcessExiter} is replaced with a {@code @MockBean}: ArqSyncPipelineRunner
 * is itself an ApplicationRunner, and with no program arguments in this test
 * context it would take the "missing argument" path and call
 * {@code processExiter.exit(1)} - the real {@link SystemProcessExiter} would call
 * {@code System.exit} for real and kill the test JVM.
 */
@SpringBootTest
class ArqSyncPipelineRunnerSpringContextTest {

    @MockitoBean
    private ProcessExiter processExiter;

    @Autowired
    private ArqSyncPipelineRunner pipelineRunner;

    @Autowired
    private ScannerService scannerService;

    @Autowired
    private DependencyAnalyzer dependencyAnalyzer;

    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private ReportExporter reportExporter;

    @Test
    void contextLoadsWithAllPipelineBeansWired() {
        assertThat(pipelineRunner).isNotNull();
        assertThat(scannerService).isNotNull();
        assertThat(dependencyAnalyzer).isNotNull();
        assertThat(persistenceService).isNotNull();
        assertThat(reportExporter).isNotNull();
        assertThat(processExiter).isNotNull();
        // H2 (the test database) is reachable, so the real, JPA-backed implementation
        // should be wired here, not the NoOpPersistenceService fallback.
        assertThat(persistenceService).isInstanceOf(DefaultPersistenceService.class);
    }
}
