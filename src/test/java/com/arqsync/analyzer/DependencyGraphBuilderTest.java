package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphBuilderTest {

    private final DependencyGraphBuilder builder = new DefaultDependencyGraphBuilder();

    @Test
    void internalImportGeneratesPackageToPackageEdge() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImporting("com.acme.controller", "OrderController", "com.acme.service", "OrderService")
                .classImporting("com.acme.service", "OrderService", "com.acme.repository", "OrderRepository")
                .build();

        DependencyGraph graph = builder.build(scan);

        assertThat(graph.nodes()).containsExactlyInAnyOrder(
                new PackageName("com.acme.controller"),
                new PackageName("com.acme.service"),
                new PackageName("com.acme.repository")
        );
        assertThat(graph.edges()).hasSize(2);
        assertThat(graph.outgoingFrom(new PackageName("com.acme.controller")))
                .extracting(DependencyEdge::to)
                .containsExactly(new PackageName("com.acme.service"));
    }

    @Test
    void externalImportIsIgnored() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classWithImports("com.acme.controller", "OrderController", "org.springframework.stereotype.Component")
                .build();

        DependencyGraph graph = builder.build(scan);

        assertThat(graph.nodes()).containsExactly(new PackageName("com.acme.controller"));
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void selfImportIsIgnored() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classWithImports("com.acme.controller", "OrderController", "com.acme.controller.OrderHelper")
                .build();

        DependencyGraph graph = builder.build(scan);

        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void multipleImportsBetweenSamePackagesAggregateIntoOneEdgeWithLimitedSamples() {
        ProjectScanFixtures.Builder fixtureBuilder = ProjectScanFixtures.builder();
        for (int i = 1; i <= 8; i++) {
            fixtureBuilder.classImporting("com.acme.controller", "Controller" + i, "com.acme.service", "Service" + i);
        }
        ProjectScan scan = fixtureBuilder.build();

        DependencyGraph graph = builder.build(scan);

        assertThat(graph.edges()).hasSize(1);
        DependencyEdge edge = graph.edges().get(0);
        assertThat(edge.from()).isEqualTo(new PackageName("com.acme.controller"));
        assertThat(edge.to()).isEqualTo(new PackageName("com.acme.service"));
        assertThat(edge.occurrences()).isEqualTo(8);
        assertThat(edge.classSamples()).hasSize(5);
    }

    @Test
    void projectWithoutInternalImportsHasIsolatedNodesAndNoEdges() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .emptyPackage("com.acme.controller")
                .emptyPackage("com.acme.service")
                .build();

        DependencyGraph graph = builder.build(scan);

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.edges()).isEmpty();
    }

    @Test
    void staticImportResolvesToTheDeclaringClassPackage() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classWithImports("com.acme.service", "OrderService", "static com.acme.util.Constants.MAX_VALUE")
                .emptyPackage("com.acme.util")
                .build();

        DependencyGraph graph = builder.build(scan);

        assertThat(graph.edges()).hasSize(1);
        DependencyEdge edge = graph.edges().get(0);
        assertThat(edge.to()).isEqualTo(new PackageName("com.acme.util"));
        assertThat(edge.classSamples().get(0).toClass()).isEqualTo("Constants");
    }

    @Test
    void wildcardImportResolvesToThePackageItself() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classWithImports("com.acme.service", "OrderService", "com.acme.repository.*")
                .emptyPackage("com.acme.repository")
                .build();

        DependencyGraph graph = builder.build(scan);

        assertThat(graph.edges()).hasSize(1);
        assertThat(graph.edges().get(0).to()).isEqualTo(new PackageName("com.acme.repository"));
    }
}
