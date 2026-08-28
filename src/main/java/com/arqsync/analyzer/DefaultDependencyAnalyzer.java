package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultDependencyAnalyzer implements DependencyAnalyzer {

    private final DependencyGraphBuilder graphBuilder;
    private final CycleDetector cycleDetector;
    private final LayerViolationDetector layerViolationDetector;
    private final MetricsCalculator metricsCalculator;
    private final ArchitectureStyleDetector architectureStyleDetector;

    public DefaultDependencyAnalyzer(
            DependencyGraphBuilder graphBuilder,
            CycleDetector cycleDetector,
            LayerViolationDetector layerViolationDetector,
            MetricsCalculator metricsCalculator,
            ArchitectureStyleDetector architectureStyleDetector
    ) {
        this.graphBuilder = graphBuilder;
        this.cycleDetector = cycleDetector;
        this.layerViolationDetector = layerViolationDetector;
        this.metricsCalculator = metricsCalculator;
        this.architectureStyleDetector = architectureStyleDetector;
    }

    @Override
    public AnalysisResult analyze(ProjectScan projectScan) {
        DependencyGraph graph = graphBuilder.build(projectScan);
        List<Cycle> cycles = cycleDetector.detect(graph);
        List<LayerViolation> violations = layerViolationDetector.detect(graph);
        AnalysisMetrics metrics = metricsCalculator.calculate(projectScan, graph, cycles, violations);
        ArchitectureStyle architectureStyle = architectureStyleDetector.detect(graph);

        return new AnalysisResult(graph, cycles, violations, metrics, architectureStyle);
    }
}
