package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.analyzer.PackageDependencyCount;
import com.arqsync.analyzer.PackageName;
import com.arqsync.scanner.ClassScan;
import com.arqsync.scanner.PackageScan;
import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisResultMapperTest {

    private final AnalysisResultMapper mapper = new AnalysisResultMapper();

    private ProjectScan projectScanWith(String rootPath, String packageName, int classCount) {
        List<ClassScan> classes = java.util.stream.IntStream.range(0, classCount)
                .mapToObj(i -> new ClassScan("Class" + i, packageName, List.of()))
                .toList();
        return new ProjectScan(rootPath, List.of(new PackageScan(packageName, classes)), List.of());
    }

    private AnalysisResult analysisResultWith(
            int totalPackages,
            int totalClasses,
            List<com.arqsync.analyzer.Cycle> cycles,
            List<com.arqsync.analyzer.LayerViolation> violations,
            List<PackageDependencyCount> dependencyCounts
    ) {
        DependencyGraph graph = new DependencyGraph(Set.of(), List.of());
        AnalysisMetrics metrics = new AnalysisMetrics(
                totalPackages, totalClasses, cycles.size(), violations.size(), dependencyCounts
        );
        return new AnalysisResult(graph, cycles, violations, metrics);
    }

    @Test
    void mapsProjectAnalysisPackageMetricsAndCycles() {
        ProjectScan projectScan = projectScanWith("/repo/my-project", "com.acme.controller", 2);
        AnalysisResult analysisResult = analysisResultWith(
                1,
                2,
                List.of(new com.arqsync.analyzer.Cycle(List.of(
                        new PackageName("com.acme.a"), new PackageName("com.acme.b"), new PackageName("com.acme.a")
                ))),
                List.of(),
                List.of(new PackageDependencyCount(new PackageName("com.acme.controller"), 1, 2))
        );

        Project project = mapper.mapProject(projectScan, LocalDateTime.of(2026, 1, 1, 0, 0));
        Analysis analysis = mapper.mapAnalysis(projectScan, analysisResult, LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThat(project.getPath()).isEqualTo("/repo/my-project");
        assertThat(project.getName()).isEqualTo("my-project");

        assertThat(analysis.getTotalPackages()).isEqualTo(1);
        assertThat(analysis.getTotalClasses()).isEqualTo(2);
        assertThat(analysis.getCyclicDependencies()).isEqualTo(1);
        assertThat(analysis.getViolationCount()).isZero();

        assertThat(analysis.getPackageMetrics()).hasSize(1);
        PackageMetric metric = analysis.getPackageMetrics().get(0);
        assertThat(metric.getPackageName()).isEqualTo("com.acme.controller");
        assertThat(metric.getClassCount()).isEqualTo(2);
        assertThat(metric.getIncomingDependencies()).isEqualTo(1);
        assertThat(metric.getOutgoingDependencies()).isEqualTo(2);

        assertThat(analysis.getCycles()).hasSize(1);
        Cycle cycle = analysis.getCycles().get(0);
        assertThat(cycle.getCyclePath()).isEqualTo("com.acme.a -> com.acme.b -> com.acme.a");
        assertThat(cycle.getLength()).isEqualTo(2);
    }

    @Test
    void resultWithoutCyclesOrViolationsMapsToZeroedAnalysisWithEmptyCycleList() {
        ProjectScan projectScan = projectScanWith("/repo/clean-project", "com.acme", 1);
        AnalysisResult analysisResult = analysisResultWith(1, 1, List.of(), List.of(), List.of());

        Analysis analysis = mapper.mapAnalysis(projectScan, analysisResult, LocalDateTime.now());

        assertThat(analysis.getCyclicDependencies()).isZero();
        assertThat(analysis.getViolationCount()).isZero();
        assertThat(analysis.getCycles()).isEmpty();
    }

    @Test
    void derivesProjectNameFromLastPathSegmentIncludingTrailingSlash() {
        ProjectScan withoutTrailingSlash = projectScanWith("/home/user/projects/arqsync", "com.acme", 0);
        ProjectScan withTrailingSlash = projectScanWith("/home/user/projects/arqsync/", "com.acme", 0);

        Project withoutSlash = mapper.mapProject(withoutTrailingSlash, LocalDateTime.now());
        Project withSlash = mapper.mapProject(withTrailingSlash, LocalDateTime.now());

        assertThat(withoutSlash.getName()).isEqualTo("arqsync");
        assertThat(withSlash.getName()).isEqualTo("arqsync");
    }
}
