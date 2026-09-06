package com.arqsync.analyzer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPackageRoleClassifierTest {

    private final PackageRoleClassifier classifier = new DefaultPackageRoleClassifier();

    private DependencyGraph graphWith(String... packageNames) {
        Set<PackageName> nodes = java.util.Arrays.stream(packageNames)
                .map(PackageName::new)
                .collect(java.util.stream.Collectors.toSet());
        return new DependencyGraph(nodes, List.of());
    }

    @Test
    void hexagonalStyleClassifiesPortPackagesAsOutputPortAndUsecasePackagesAsInputPort() {
        DependencyGraph graph = graphWith(
                "com.acme.port", "com.acme.ports", "com.acme.usecase", "com.acme.usecases", "com.acme.other"
        );

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.get(new PackageName("com.acme.port"))).isEqualTo(PackageRole.OUTPUT_PORT);
        assertThat(roles.get(new PackageName("com.acme.ports"))).isEqualTo(PackageRole.OUTPUT_PORT);
        assertThat(roles.get(new PackageName("com.acme.usecase"))).isEqualTo(PackageRole.INPUT_PORT);
        assertThat(roles.get(new PackageName("com.acme.usecases"))).isEqualTo(PackageRole.INPUT_PORT);
        assertThat(roles.get(new PackageName("com.acme.other"))).isEqualTo(PackageRole.UNKNOWN);
    }

    @Test
    void adapterPackageWithNoDirectionSignalResolvesToPlainAdapter() {
        DependencyGraph graph = graphWith("com.acme.adapter", "com.acme.adapters");

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.get(new PackageName("com.acme.adapter"))).isEqualTo(PackageRole.ADAPTER);
        assertThat(roles.get(new PackageName("com.acme.adapters"))).isEqualTo(PackageRole.ADAPTER);
    }

    @Test
    void adapterPackageWithInSegmentIsDrivingEvenWhenFarFromTheAdapterSegment() {
        // Real layout observed via dogfooding against a Hexagonal project
        // (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 2.2): "in" sits three
        // segments away from "adapters", with "web" and "controller" between them.
        DependencyGraph graph = graphWith("com.boardly.adapters.in.web.controller");

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.get(new PackageName("com.boardly.adapters.in.web.controller")))
                .isEqualTo(PackageRole.DRIVING_ADAPTER);
    }

    @Test
    void adapterPackageWithOutSegmentIsDrivenEvenWhenTheAdapterSegmentIsRightmost() {
        // Real layout observed via dogfooding: the rightmost segment is itself
        // literally "adapter" (a subpackage), with "out" two positions to its left.
        DependencyGraph graph = graphWith("com.boardly.adapters.out.mongo.adapter");

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.get(new PackageName("com.boardly.adapters.out.mongo.adapter")))
                .isEqualTo(PackageRole.DRIVEN_ADAPTER);
    }

    @Test
    void drivingKeywordAndDrivenKeywordAreRecognizedAlongsideInAndOut() {
        DependencyGraph graph = graphWith("com.acme.adapters.driving.controller", "com.acme.adapters.driven.repo");

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.get(new PackageName("com.acme.adapters.driving.controller")))
                .isEqualTo(PackageRole.DRIVING_ADAPTER);
        assertThat(roles.get(new PackageName("com.acme.adapters.driven.repo")))
                .isEqualTo(PackageRole.DRIVEN_ADAPTER);
    }

    @Test
    void dataOrTranslationSegmentsCutTheSearchEvenInsideAnAdapterPackage() {
        // Real cases from dogfooding (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 4):
        // none of these are behavioral adapters even though they live inside one.
        DependencyGraph graph = graphWith(
                "com.acme.adapters.in.web.dto.project",
                "com.acme.adapters.in.web.exception",
                "com.acme.adapters.out.mongo.mapper",
                "com.acme.adapters.out.mongo.document"
        );

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.values()).allMatch(role -> role == PackageRole.UNKNOWN);
    }

    @Test
    void nonHexagonalStyleClassifiesEveryPackageAsUnknownRegardlessOfName() {
        DependencyGraph graph = graphWith("com.acme.port", "com.acme.adapter", "com.acme.controller");

        Map<PackageName, PackageRole> roles = classifier.classify(graph,
                new ArchitectureStyle("Arquitetura em Camadas (Layered)", "descrição"));

        assertThat(roles.values()).allMatch(role -> role == PackageRole.UNKNOWN);
    }

    @Test
    void unknownStyleClassifiesEveryPackageAsUnknown() {
        DependencyGraph graph = graphWith("com.acme.port", "com.acme.adapter");

        Map<PackageName, PackageRole> roles = classifier.classify(graph, new ArchitectureStyle("Não identificado", ""));

        assertThat(roles.values()).allMatch(role -> role == PackageRole.UNKNOWN);
    }

    @Test
    void rightmostSegmentWinsWhenAPackageHasBothPortAndAdapterKeywordsAtDifferentDepths() {
        DependencyGraph graph = graphWith("com.acme.port.adapter");

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        // "adapter" (rightmost) wins the role signal over "port"; no direction
        // keyword is present, so it resolves to the ambiguous ADAPTER fallback.
        assertThat(roles.get(new PackageName("com.acme.port.adapter"))).isEqualTo(PackageRole.ADAPTER);
    }
}
