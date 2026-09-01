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
    void hexagonalStyleClassifiesPortPackagesAsCoreAndAdapterPackagesAsAdapter() {
        DependencyGraph graph = graphWith(
                "com.acme.port", "com.acme.ports", "com.acme.adapter", "com.acme.adapters", "com.acme.other"
        );

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.get(new PackageName("com.acme.port"))).isEqualTo(PackageRole.CORE);
        assertThat(roles.get(new PackageName("com.acme.ports"))).isEqualTo(PackageRole.CORE);
        assertThat(roles.get(new PackageName("com.acme.adapter"))).isEqualTo(PackageRole.ADAPTER);
        assertThat(roles.get(new PackageName("com.acme.adapters"))).isEqualTo(PackageRole.ADAPTER);
        assertThat(roles.get(new PackageName("com.acme.other"))).isEqualTo(PackageRole.UNKNOWN);
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
    void rightmostSegmentWinsWhenAPackageHasBothKeywordsAtDifferentDepths() {
        DependencyGraph graph = graphWith("com.acme.port.adapter");

        Map<PackageName, PackageRole> roles = classifier.classify(graph, DefaultArchitectureStyleDetector.HEXAGONAL);

        assertThat(roles.get(new PackageName("com.acme.port.adapter"))).isEqualTo(PackageRole.ADAPTER);
    }
}
