package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;

import java.util.List;

public interface MetricsCalculator {

    AnalysisMetrics calculate(
            ProjectScan projectScan,
            DependencyGraph graph,
            List<Cycle> cycles,
            List<LayerViolation> violations
    );
}
