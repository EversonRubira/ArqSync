package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultDependencyAnalyzerTest {

    private final DependencyAnalyzer analyzer = new DefaultDependencyAnalyzer(
            new DefaultDependencyGraphBuilder(),
            new DefaultCycleDetector(),
            new DefaultLayerViolationDetector(),
            new DefaultMetricsCalculator(),
            new DefaultArchitectureStyleDetector()
    );

    @Test
    void assemblesACompositeAnalysisResultWithACycleAndAViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                // a well-formed, violation-free chain
                .classImporting("com.acme.controller", "OrderController", "com.acme.service", "OrderService")
                .classImporting("com.acme.service", "OrderService", "com.acme.repository", "OrderRepository")
                // a layer skip violation
                .classImporting("com.acme.controller", "UserController", "com.acme.repository", "UserRepository")
                // an independent two-node cycle
                .classImporting("com.acme.x", "X", "com.acme.y", "Y")
                .classImporting("com.acme.y", "Y", "com.acme.x", "X")
                .build();

        AnalysisResult result = analyzer.analyze(scan);

        assertThat(result.dependencyGraph().nodes()).hasSize(5);
        assertThat(result.cycles()).hasSize(1);
        assertThat(result.cycles().get(0).path())
                .extracting(PackageName::value)
                .containsExactly("com.acme.x", "com.acme.y", "com.acme.x");

        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().get(0).type()).isEqualTo(ViolationType.LAYER_SKIP);
        assertThat(result.violations().get(0).from()).isEqualTo(new PackageName("com.acme.controller"));
        assertThat(result.violations().get(0).to()).isEqualTo(new PackageName("com.acme.repository"));

        assertThat(result.metrics().totalPackages()).isEqualTo(5);
        // com.acme.repository is only ever an import target in this fixture, so it exists
        // as a package (2 classes come from controller, 1 from service, 1 from x, 1 from y).
        assertThat(result.metrics().totalClasses()).isEqualTo(5);
        assertThat(result.metrics().cycleCount()).isEqualTo(1);
        assertThat(result.metrics().violationCount()).isEqualTo(1);

        assertThat(result.architectureStyle().name()).isEqualTo("Arquitetura em Camadas (Layered)");
    }

    @Test
    void emptyProjectProducesAnEmptyAnalysisResultWithoutException() {
        AnalysisResult result = analyzer.analyze(ProjectScanFixtures.empty());

        assertThat(result.dependencyGraph().nodes()).isEmpty();
        assertThat(result.dependencyGraph().edges()).isEmpty();
        assertThat(result.cycles()).isEmpty();
        assertThat(result.violations()).isEmpty();
        assertThat(result.metrics().totalPackages()).isZero();
        assertThat(result.metrics().totalClasses()).isZero();
        assertThat(result.metrics().cycleCount()).isZero();
        assertThat(result.metrics().violationCount()).isZero();
        assertThat(result.metrics().dependencyCounts()).isEmpty();
        assertThat(result.architectureStyle().name()).isEqualTo("Não identificado");
    }
}
