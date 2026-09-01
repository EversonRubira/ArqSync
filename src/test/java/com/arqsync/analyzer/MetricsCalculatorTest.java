package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class MetricsCalculatorTest {

    private final DependencyGraphBuilder graphBuilder = new DefaultDependencyGraphBuilder();
    private final CycleDetector cycleDetector = new DefaultCycleDetector();
    private final LayerViolationDetector layerViolationDetector = new DefaultLayerViolationDetector();
    private final MetricsCalculator metricsCalculator = new DefaultMetricsCalculator();

    @Test
    void countsPackagesAndClassesFromProjectScan() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classWithImports("com.acme.controller", "OrderController")
                .classWithImports("com.acme.controller", "UserController")
                .classWithImports("com.acme.service", "OrderService")
                .build();

        DependencyGraph graph = graphBuilder.build(scan);
        AnalysisMetrics metrics = metricsCalculator.calculate(scan, graph, List.of(), List.of(), List.of());

        assertThat(metrics.totalPackages()).isEqualTo(2);
        assertThat(metrics.totalClasses()).isEqualTo(3);
    }

    @Test
    void cycleAndViolationCountsMatchTheSizeOfTheProvidedLists() {
        ProjectScan scan = ProjectScanFixtures.withCycle("com.acme.a", "com.acme.b");
        DependencyGraph graph = graphBuilder.build(scan);
        List<Cycle> cycles = cycleDetector.detect(graph);
        List<LayerViolation> violations = layerViolationDetector.detect(graph);

        AnalysisMetrics metrics = metricsCalculator.calculate(scan, graph, cycles, violations, List.of());

        assertThat(metrics.cycleCount()).isEqualTo(cycles.size());
        assertThat(metrics.violationCount()).isEqualTo(violations.size());
    }

    @Test
    void calculatesIncomingAndOutgoingDependencyCountsPerPackage() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImporting("com.acme.controller", "OrderController", "com.acme.service", "OrderService")
                .classImporting("com.acme.service", "OrderService", "com.acme.repository", "OrderRepository")
                .emptyPackage("com.acme.isolated")
                .build();

        DependencyGraph graph = graphBuilder.build(scan);
        AnalysisMetrics metrics = metricsCalculator.calculate(scan, graph, List.of(), List.of(), List.of());

        assertThat(metrics.dependencyCounts())
                .extracting(
                        count -> count.pkg().value(),
                        PackageDependencyCount::incoming,
                        PackageDependencyCount::outgoing
                )
                .containsExactlyInAnyOrder(
                        tuple("com.acme.controller", 0, 1),
                        tuple("com.acme.service", 1, 1),
                        tuple("com.acme.repository", 1, 0),
                        tuple("com.acme.isolated", 0, 0)
                );
    }
}
