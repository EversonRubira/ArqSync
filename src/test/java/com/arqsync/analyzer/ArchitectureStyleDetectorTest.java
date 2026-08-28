package com.arqsync.analyzer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureStyleDetectorTest {

    private final ArchitectureStyleDetector detector = new DefaultArchitectureStyleDetector();

    private DependencyGraph graphOf(String... packageNames) {
        Set<PackageName> nodes = Stream.of(packageNames).map(PackageName::new).collect(java.util.stream.Collectors.toSet());
        return new DependencyGraph(nodes, List.of());
    }

    @Test
    void emptyProjectIsNotIdentified() {
        ArchitectureStyle style = detector.detect(graphOf());

        assertThat(style.name()).isEqualTo("Não identificado");
    }

    @Test
    void unrecognizedPackageNamesAreNotIdentified() {
        ArchitectureStyle style = detector.detect(graphOf("com.acme.utils", "com.acme.helpers"));

        assertThat(style.name()).isEqualTo("Não identificado");
    }

    @Test
    void controllerServiceRepositoryIsDetectedAsLayered() {
        ArchitectureStyle style = detector.detect(graphOf(
                "com.acme.controller", "com.acme.service", "com.acme.repository", "com.acme.domain"
        ));

        assertThat(style.name()).isEqualTo("Arquitetura em Camadas (Layered)");
        assertThat(style.description()).isNotBlank();
    }

    @Test
    void portsAndAdaptersIsDetectedAsHexagonal() {
        ArchitectureStyle style = detector.detect(graphOf(
                "com.acme.domain", "com.acme.application.port", "com.acme.adapter.web", "com.acme.adapter.persistence"
        ));

        assertThat(style.name()).isEqualTo("Arquitetura Hexagonal (Ports & Adapters)");
    }

    @Test
    void useCasesAndEntitiesIsDetectedAsCleanArchitecture() {
        ArchitectureStyle style = detector.detect(graphOf(
                "com.acme.entities", "com.acme.usecases", "com.acme.gateways", "com.acme.infrastructure"
        ));

        assertThat(style.name()).isEqualTo("Clean Architecture");
    }

    @Test
    void aggregatesAndValueObjectsIsDetectedAsDdd() {
        ArchitectureStyle style = detector.detect(graphOf(
                "com.acme.domain.aggregate", "com.acme.domain.valueobject", "com.acme.application"
        ));

        assertThat(style.name()).isEqualTo("Domain-Driven Design (DDD)");
    }

    @Test
    void domainApplicationInfrastructureTriadFallsBackToDdd() {
        ArchitectureStyle style = detector.detect(graphOf(
                "com.acme.domain", "com.acme.application", "com.acme.infrastructure"
        ));

        assertThat(style.name()).isEqualTo("Domain-Driven Design (DDD)");
    }

    @Test
    void explicitStyleNameInPackageWins() {
        assertThat(detector.detect(graphOf("com.acme.hexagonal.core")).name())
                .isEqualTo("Arquitetura Hexagonal (Ports & Adapters)");
        assertThat(detector.detect(graphOf("com.acme.clean.core")).name())
                .isEqualTo("Clean Architecture");
        assertThat(detector.detect(graphOf("com.acme.ddd.core")).name())
                .isEqualTo("Domain-Driven Design (DDD)");
    }
}
