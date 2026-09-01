package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAdapterPortViolationDetectorTest {

    private final AdapterPortViolationDetector detector = new DefaultAdapterPortViolationDetector();

    private static final ArchitectureStyle LAYERED =
            new ArchitectureStyle("Arquitetura em Camadas (Layered)", "descrição");

    @Test
    void nonHexagonalStyleNeverProducesAViolationEvenWithAnObviousMissingPort() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapter", "OrderAdapter", false)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.adapter"), PackageRole.ADAPTER);

        List<AdapterSemPortaViolation> violations = detector.detect(scan, LAYERED, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void adapterImplementingACoreInterfaceHasNoViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "OrderPort")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.CORE,
                new PackageName("com.acme.adapter"), PackageRole.ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void adapterWithNoSuperTypesAtAllIsAViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapter", "OrderAdapter", false)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.adapter"), PackageRole.ADAPTER);

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapter"), "OrderAdapter")
        );
    }

    @Test
    void adapterImplementingOnlyAnExternalTypeIsAViolation() {
        // "Serializable" never appears as a scanned interface in a core package,
        // so it can never satisfy the check - same as any other external type.
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "Serializable")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.adapter"), PackageRole.ADAPTER);

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapter"), "OrderAdapter")
        );
    }

    @Test
    void adapterImplementingBothAnExternalTypeAndTheCorePortHasNoViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "Serializable", "OrderPort")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.CORE,
                new PackageName("com.acme.adapter"), PackageRole.ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void aClassInACorePackageIsNeverCheckedForAPortItself() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.port"), PackageRole.CORE);

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void onlyTheAdapterMissingAPortIsFlaggedAmongSeveral() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "CompliantAdapter", false, "OrderPort")
                .classImplementing("com.acme.adapter", "BrokenAdapter", false)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.CORE,
                new PackageName("com.acme.adapter"), PackageRole.ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapter"), "BrokenAdapter")
        );
    }

    @Test
    void adapterImplementingANonInterfaceTypeWithTheSameNameAsAPortStillCountsByNameOnly() {
        // The detector matches simple names against scanned core-package
        // interfaces - it doesn't re-verify isInterface() on the adapter side,
        // only on the core side when building the port-name registry.
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "OrderPort")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.CORE,
                new PackageName("com.acme.adapter"), PackageRole.ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void aClassInAPackageWithNoRoleEntryIsTreatedAsUnknownAndNeverChecked() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapter", "OrderAdapter", false)
                .build();

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, Map.of());

        assertThat(violations).isEmpty();
    }
}
