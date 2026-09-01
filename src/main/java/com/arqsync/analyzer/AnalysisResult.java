package com.arqsync.analyzer;

import java.util.List;

/**
 * The complete output of the Analyzer for one {@code ProjectScan}: the
 * dependency graph, the cycles and layer violations found in it, and
 * descriptive metrics. Consumed by Persistence and Exporter.
 *
 * <p>{@code adapterPortViolations} is a separate list from {@code violations}
 * rather than a unified violation type (SPEC-adapter-port-violation.md, 5) —
 * always empty unless {@code architectureStyle} is Hexagonal. The report
 * presents both in the same "Violações" section regardless.
 */
public record AnalysisResult(
        DependencyGraph dependencyGraph,
        List<Cycle> cycles,
        List<LayerViolation> violations,
        List<AdapterSemPortaViolation> adapterPortViolations,
        AnalysisMetrics metrics,
        ArchitectureStyle architectureStyle
) {

    public AnalysisResult {
        cycles = List.copyOf(cycles);
        violations = List.copyOf(violations);
        adapterPortViolations = List.copyOf(adapterPortViolations);
    }
}
