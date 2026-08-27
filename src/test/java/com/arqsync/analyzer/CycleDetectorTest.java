package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CycleDetectorTest {

    private final DependencyGraphBuilder graphBuilder = new DefaultDependencyGraphBuilder();
    private final CycleDetector cycleDetector = new DefaultCycleDetector();

    private DependencyGraph graphOf(ProjectScan scan) {
        return graphBuilder.build(scan);
    }

    @Test
    void noCyclesReturnsEmptyList() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImporting("com.acme.controller", "OrderController", "com.acme.service", "OrderService")
                .classImporting("com.acme.service", "OrderService", "com.acme.repository", "OrderRepository")
                .build();

        List<Cycle> cycles = cycleDetector.detect(graphOf(scan));

        assertThat(cycles).isEmpty();
    }

    @Test
    void detectsSimpleTwoNodeCycle() {
        ProjectScan scan = ProjectScanFixtures.withCycle("com.acme.a", "com.acme.b");

        List<Cycle> cycles = cycleDetector.detect(graphOf(scan));

        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0).path())
                .extracting(PackageName::value)
                .containsExactly("com.acme.a", "com.acme.b", "com.acme.a");
    }

    @Test
    void detectsThreeNodeCycle() {
        ProjectScan scan = ProjectScanFixtures.withCycle("com.acme.a", "com.acme.b", "com.acme.c");

        List<Cycle> cycles = cycleDetector.detect(graphOf(scan));

        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0).path())
                .extracting(PackageName::value)
                .containsExactly("com.acme.a", "com.acme.b", "com.acme.c", "com.acme.a");
    }

    @Test
    void detectsSelfLoopAsSingleNodeCycle() {
        // A same-package import is excluded by the graph builder (2.6, self-import), so a
        // genuine self-loop edge (a package depending on itself in the graph) is built
        // directly here rather than via a ProjectScan fixture.
        DependencyGraph graphWithSelfLoop = new DependencyGraph(
                Set.of(new PackageName("com.acme.a")),
                List.of(new DependencyEdge(
                        new PackageName("com.acme.a"),
                        new PackageName("com.acme.a"),
                        1,
                        List.of(new ClassDependency("A", "A"))
                ))
        );

        List<Cycle> cycles = cycleDetector.detect(graphWithSelfLoop);

        assertThat(cycles).hasSize(1);
        assertThat(cycles.get(0).path())
                .extracting(PackageName::value)
                .containsExactly("com.acme.a", "com.acme.a");
    }

    @Test
    void diamondDependencyWithoutCycleIsNotReportedAsCycle() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImporting("com.acme.a", "A", "com.acme.b", "B")
                .classImporting("com.acme.a", "A2", "com.acme.c", "C")
                .classImporting("com.acme.b", "B", "com.acme.d", "D")
                .classImporting("com.acme.c", "C", "com.acme.d", "D")
                .build();

        List<Cycle> cycles = cycleDetector.detect(graphOf(scan));

        assertThat(cycles).isEmpty();
    }

    @Test
    void detectsTwoIndependentCyclicComponents() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImporting("com.acme.a", "A", "com.acme.b", "B")
                .classImporting("com.acme.b", "B", "com.acme.a", "A")
                .classImporting("com.acme.x", "X", "com.acme.y", "Y")
                .classImporting("com.acme.y", "Y", "com.acme.x", "X")
                .build();

        List<Cycle> cycles = cycleDetector.detect(graphOf(scan));

        assertThat(cycles).hasSize(2);
        assertThat(cycles)
                .extracting(cycle -> cycle.path().get(0).value())
                .containsExactlyInAnyOrder("com.acme.a", "com.acme.x");
    }
}
