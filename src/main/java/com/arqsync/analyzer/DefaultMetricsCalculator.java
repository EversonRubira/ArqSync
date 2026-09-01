package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DefaultMetricsCalculator implements MetricsCalculator {

    @Override
    public AnalysisMetrics calculate(
            ProjectScan projectScan,
            DependencyGraph graph,
            List<Cycle> cycles,
            List<LayerViolation> violations,
            List<AdapterSemPortaViolation> adapterPortViolations
    ) {
        int totalPackages = projectScan.packages().size();
        int totalClasses = projectScan.packages().stream()
                .mapToInt(pkg -> pkg.classes().size())
                .sum();

        List<PackageDependencyCount> dependencyCounts = graph.nodes().stream()
                .map(pkg -> new PackageDependencyCount(
                        pkg,
                        graph.incomingTo(pkg).size(),
                        graph.outgoingFrom(pkg).size()
                ))
                .sorted(Comparator.comparing(count -> count.pkg().value()))
                .toList();

        return new AnalysisMetrics(
                totalPackages,
                totalClasses,
                cycles.size(),
                violations.size() + adapterPortViolations.size(),
                dependencyCounts
        );
    }
}
