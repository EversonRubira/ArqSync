package com.arqsync.analyzer;

import java.util.List;

/**
 * The complete output of the Analyzer for one {@code ProjectScan}: the
 * dependency graph, the cycles and layer violations found in it, and
 * descriptive metrics. Consumed by Persistence and Exporter.
 */
public record AnalysisResult(
        DependencyGraph dependencyGraph,
        List<Cycle> cycles,
        List<LayerViolation> violations,
        AnalysisMetrics metrics
) {

    public AnalysisResult {
        cycles = List.copyOf(cycles);
        violations = List.copyOf(violations);
    }
}
