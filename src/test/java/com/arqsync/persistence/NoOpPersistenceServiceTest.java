package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.ArchitectureStyle;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;

class NoOpPersistenceServiceTest {

    private final PersistenceService service = new NoOpPersistenceService();

    @Test
    void saveNeverThrows() {
        ProjectScan projectScan = new ProjectScan("/repo/x", List.of(), List.of());
        AnalysisResult analysisResult = new AnalysisResult(
                new DependencyGraph(Set.of(), List.of()),
                List.of(),
                List.of(),
                List.of(),
                new AnalysisMetrics(0, 0, 0, 0, List.of()),
                new ArchitectureStyle("Não identificado", "")
        );

        assertThatCode(() -> service.save(projectScan, analysisResult)).doesNotThrowAnyException();
    }
}
