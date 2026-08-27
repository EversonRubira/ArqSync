package com.arqsync.analyzer;

import java.util.List;

/**
 * Descriptive metrics of one analysis run.
 */
public record AnalysisMetrics(
        int totalPackages,
        int totalClasses,
        int cycleCount,
        int violationCount,
        List<PackageDependencyCount> dependencyCounts
) {

    public AnalysisMetrics {
        dependencyCounts = List.copyOf(dependencyCounts);
    }
}
