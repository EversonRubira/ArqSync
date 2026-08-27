package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.PackageDependencyCount;
import com.arqsync.scanner.ProjectScan;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Converts {@link ProjectScan} + {@link AnalysisResult} into the JPA entities
 * to persist. A pure function, no Spring/JPA annotations, no I/O
 * (SPEC-persistence.md, section 4) — testable without a database.
 */
class AnalysisResultMapper {

    Project mapProject(ProjectScan projectScan, LocalDateTime createdAt) {
        String path = projectScan.rootPath();
        return new Project(path, projectNameFrom(path), null, createdAt);
    }

    Analysis mapAnalysis(ProjectScan projectScan, AnalysisResult analysisResult, LocalDateTime analyzedAt) {
        Analysis analysis = new Analysis(
                analyzedAt,
                analysisResult.metrics().totalPackages(),
                analysisResult.metrics().totalClasses(),
                analysisResult.dependencyGraph().edges().size(),
                analysisResult.metrics().cycleCount(),
                analysisResult.metrics().violationCount()
        );

        for (PackageDependencyCount count : analysisResult.metrics().dependencyCounts()) {
            analysis.addPackageMetric(new PackageMetric(
                    count.pkg().value(),
                    classCountFor(projectScan, count.pkg().value()),
                    count.outgoing(),
                    count.incoming()
            ));
        }

        for (com.arqsync.analyzer.Cycle cycle : analysisResult.cycles()) {
            analysis.addCycle(mapCycle(cycle));
        }

        return analysis;
    }

    private Cycle mapCycle(com.arqsync.analyzer.Cycle cycle) {
        List<String> names = cycle.path().stream()
                .map(pkg -> pkg.value())
                .toList();
        String cyclePath = String.join(" -> ", names);
        int length = cycle.path().size() - 1;
        return new Cycle(cyclePath, length);
    }

    private int classCountFor(ProjectScan projectScan, String packageName) {
        return projectScan.packages().stream()
                .filter(pkg -> pkg.name().equals(packageName))
                .findFirst()
                .map(pkg -> pkg.classes().size())
                .orElse(0);
    }

    private String projectNameFrom(String path) {
        String normalized = (path.endsWith("/") || path.endsWith("\\"))
                ? path.substring(0, path.length() - 1)
                : path;
        int lastSlash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
}
