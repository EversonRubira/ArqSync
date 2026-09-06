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
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.adapter"), PackageRole.DRIVEN_ADAPTER);

        List<AdapterSemPortaViolation> violations = detector.detect(scan, LAYERED, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void adapterWithAmbiguousDirectionIsNeverChecked() {
        // PackageRole.ADAPTER means the classifier found an adapter package but
        // couldn't resolve its direction (ADENDO-SPEC-analyzer-adapter-porta-direcao.md,
        // 2.1-2.2) - same "not applicable is not a miss" philosophy as UNKNOWN.
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapter", "OrderAdapter", false)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.adapter"), PackageRole.ADAPTER);

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    // ---- driven adapter (implements an output port) ----

    @Test
    void drivenAdapterImplementingAnOutputPortInterfaceHasNoViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "OrderPort")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.OUTPUT_PORT,
                new PackageName("com.acme.adapter"), PackageRole.DRIVEN_ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void drivenAdapterWithNoSuperTypesAtAllIsAViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapter", "OrderAdapter", false)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.adapter"), PackageRole.DRIVEN_ADAPTER);

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapter"), "OrderAdapter",
                        PackageRole.DRIVEN_ADAPTER)
        );
    }

    @Test
    void drivenAdapterImplementingOnlyAnExternalTypeIsAViolation() {
        // "Serializable" never appears as a scanned interface in an output-port
        // package, so it can never satisfy the check - same as any other external type.
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "Serializable")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.adapter"), PackageRole.DRIVEN_ADAPTER);

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapter"), "OrderAdapter",
                        PackageRole.DRIVEN_ADAPTER)
        );
    }

    @Test
    void drivenAdapterImplementingBothAnExternalTypeAndTheOutputPortHasNoViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "Serializable", "OrderPort")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.OUTPUT_PORT,
                new PackageName("com.acme.adapter"), PackageRole.DRIVEN_ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void aClassInAnOutputPortPackageIsNeverCheckedForAPortItself() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(new PackageName("com.acme.port"), PackageRole.OUTPUT_PORT);

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void onlyTheDrivenAdapterMissingAPortIsFlaggedAmongSeveral() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "CompliantAdapter", false, "OrderPort")
                .classImplementing("com.acme.adapter", "BrokenAdapter", false)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.OUTPUT_PORT,
                new PackageName("com.acme.adapter"), PackageRole.DRIVEN_ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapter"), "BrokenAdapter",
                        PackageRole.DRIVEN_ADAPTER)
        );
    }

    @Test
    void drivenAdapterImplementingANonInterfaceTypeWithTheSameNameAsAPortStillCountsByNameOnly() {
        // The detector matches simple names against scanned output-port-package
        // interfaces - it doesn't re-verify isInterface() on the adapter side,
        // only on the port side when building the port-name registry.
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.port", "OrderPort", true)
                .classImplementing("com.acme.adapter", "OrderAdapter", false, "OrderPort")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.port"), PackageRole.OUTPUT_PORT,
                new PackageName("com.acme.adapter"), PackageRole.DRIVEN_ADAPTER
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

    // ---- driving adapter (depends on an input port) ----

    @Test
    void drivingAdapterDependingOnAnInputPortHasNoViolation() {
        // Real pattern from dogfooding against the Boardly project's
        // AuthController/ProjectController/TaskController
        // (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 1.2): a driving adapter
        // injecting a use case (input port) as a field must not be flagged - this
        // is exactly the false positive the adendo fixes.
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.usecase", "CreateProjectUseCase", true)
                .classDependingOn("com.acme.adapters.in.web", "ProjectController", "CreateProjectUseCase")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.usecase"), PackageRole.INPUT_PORT,
                new PackageName("com.acme.adapters.in.web"), PackageRole.DRIVING_ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).isEmpty();
    }

    @Test
    void drivingAdapterWithNoFieldTypesAtAllIsAViolation() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.adapters.in.web", "OrphanController", false)
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.adapters.in.web"), PackageRole.DRIVING_ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapters.in.web"), "OrphanController",
                        PackageRole.DRIVING_ADAPTER)
        );
    }

    @Test
    void drivingAdapterDependingOnlyOnAnOutputPortIsStillAViolation() {
        // Regression test for the real bug found via dogfooding: Boardly's
        // DevUserController reaches straight into UserRepositoryPort (an output
        // port), skipping the input port / use case a driving adapter is actually
        // expected to depend on (ADENDO-SPEC-analyzer-adapter-porta-direcao.md,
        // 1.2 and 2.4). Depending on *a* port isn't enough - it has to be a port
        // matching the adapter's own direction.
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.ports", "UserRepositoryPort", true)
                .classDependingOn("com.acme.adapters.in.web", "DevUserController", "UserRepositoryPort")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.ports"), PackageRole.OUTPUT_PORT,
                new PackageName("com.acme.adapters.in.web"), PackageRole.DRIVING_ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapters.in.web"), "DevUserController",
                        PackageRole.DRIVING_ADAPTER)
        );
    }

    @Test
    void onlyTheDrivingAdapterMissingAnInputPortIsFlaggedAmongSeveral() {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImplementing("com.acme.usecase", "CreateTaskUseCase", true)
                .classDependingOn("com.acme.adapters.in.web", "TaskController", "CreateTaskUseCase")
                .classDependingOn("com.acme.adapters.in.web", "DevUserController", "SomeUnrelatedType")
                .build();
        Map<PackageName, PackageRole> roles = Map.of(
                new PackageName("com.acme.usecase"), PackageRole.INPUT_PORT,
                new PackageName("com.acme.adapters.in.web"), PackageRole.DRIVING_ADAPTER
        );

        List<AdapterSemPortaViolation> violations =
                detector.detect(scan, DefaultArchitectureStyleDetector.HEXAGONAL, roles);

        assertThat(violations).containsExactly(
                new AdapterSemPortaViolation(new PackageName("com.acme.adapters.in.web"), "DevUserController",
                        PackageRole.DRIVING_ADAPTER)
        );
    }
}
