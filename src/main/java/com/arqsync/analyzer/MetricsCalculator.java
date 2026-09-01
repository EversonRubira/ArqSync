package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;

import java.util.List;

public interface MetricsCalculator {

    /**
     * {@code violationCount} in the returned metrics is
     * {@code violations.size() + adapterPortViolations.size()} — the two
     * violation lists are presented together (SPEC-adapter-port-violation.md, 5).
     */
    AnalysisMetrics calculate(
            ProjectScan projectScan,
            DependencyGraph graph,
            List<Cycle> cycles,
            List<LayerViolation> violations,
            List<AdapterSemPortaViolation> adapterPortViolations
    );
}
