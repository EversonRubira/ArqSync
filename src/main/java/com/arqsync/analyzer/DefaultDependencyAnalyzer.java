package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DefaultDependencyAnalyzer implements DependencyAnalyzer {

    private final DependencyGraphBuilder graphBuilder;
    private final CycleDetector cycleDetector;
    private final LayerViolationDetector layerViolationDetector;
    private final MetricsCalculator metricsCalculator;
    private final ArchitectureStyleDetector architectureStyleDetector;
    private final PackageRoleClassifier packageRoleClassifier;
    private final AdapterPortViolationDetector adapterPortViolationDetector;

    public DefaultDependencyAnalyzer(
            DependencyGraphBuilder graphBuilder,
            CycleDetector cycleDetector,
            LayerViolationDetector layerViolationDetector,
            MetricsCalculator metricsCalculator,
            ArchitectureStyleDetector architectureStyleDetector,
            PackageRoleClassifier packageRoleClassifier,
            AdapterPortViolationDetector adapterPortViolationDetector
    ) {
        this.graphBuilder = graphBuilder;
        this.cycleDetector = cycleDetector;
        this.layerViolationDetector = layerViolationDetector;
        this.metricsCalculator = metricsCalculator;
        this.architectureStyleDetector = architectureStyleDetector;
        this.packageRoleClassifier = packageRoleClassifier;
        this.adapterPortViolationDetector = adapterPortViolationDetector;
    }

    @Override
    public AnalysisResult analyze(ProjectScan projectScan) {
        DependencyGraph graph = graphBuilder.build(projectScan);
        List<Cycle> cycles = cycleDetector.detect(graph);
        List<LayerViolation> violations = layerViolationDetector.detect(graph);
        ArchitectureStyle architectureStyle = architectureStyleDetector.detect(graph);

        Map<PackageName, PackageRole> packageRoles = packageRoleClassifier.classify(graph, architectureStyle);
        List<AdapterSemPortaViolation> adapterPortViolations =
                adapterPortViolationDetector.detect(projectScan, architectureStyle, packageRoles);

        AnalysisMetrics metrics = metricsCalculator.calculate(
                projectScan, graph, cycles, violations, adapterPortViolations);

        return new AnalysisResult(graph, cycles, violations, adapterPortViolations, metrics, architectureStyle);
    }
}
