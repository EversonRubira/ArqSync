package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LayerViolationDetectorTest {

    private final DependencyGraphBuilder graphBuilder = new DefaultDependencyGraphBuilder();
    private final LayerViolationDetector detector = new DefaultLayerViolationDetector();

    private List<LayerViolation> violationsFor(String fromPackage, String toPackage) {
        ProjectScan scan = ProjectScanFixtures.builder()
                .classImporting(fromPackage, "From", toPackage, "To")
                .build();
        return detector.detect(graphBuilder.build(scan));
    }

    @Test
    void controllerToServiceIsNotAViolation() {
        assertThat(violationsFor("com.acme.controller", "com.acme.service")).isEmpty();
    }

    @Test
    void serviceToRepositoryIsNotAViolation() {
        assertThat(violationsFor("com.acme.service", "com.acme.repository")).isEmpty();
    }

    @Test
    void controllerToRepositoryIsALayerSkip() {
        List<LayerViolation> violations = violationsFor("com.acme.controller", "com.acme.repository");

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).type()).isEqualTo(ViolationType.LAYER_SKIP);
        assertThat(violations.get(0).fromLayer()).isEqualTo(Layer.CONTROLLER);
        assertThat(violations.get(0).toLayer()).isEqualTo(Layer.REPOSITORY);
    }

    @Test
    void repositoryToServiceIsALayerInversion() {
        List<LayerViolation> violations = violationsFor("com.acme.repository", "com.acme.service");

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).type()).isEqualTo(ViolationType.LAYER_INVERSION);
    }

    @Test
    void repositoryToControllerIsALayerInversion() {
        List<LayerViolation> violations = violationsFor("com.acme.repository", "com.acme.controller");

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).type()).isEqualTo(ViolationType.LAYER_INVERSION);
    }

    @Test
    void domainToServiceIsALayerInversion() {
        List<LayerViolation> violations = violationsFor("com.acme.domain", "com.acme.service");

        assertThat(violations).hasSize(1);
        assertThat(violations.get(0).type()).isEqualTo(ViolationType.LAYER_INVERSION);
    }

    @Test
    void anyLayerToDomainIsNeverAViolation() {
        assertThat(violationsFor("com.acme.controller", "com.acme.domain")).isEmpty();
        assertThat(violationsFor("com.acme.service", "com.acme.domain")).isEmpty();
        assertThat(violationsFor("com.acme.repository", "com.acme.domain")).isEmpty();
    }

    @Test
    void unknownLayerOnEitherEndIsNeverAViolation() {
        assertThat(violationsFor("com.acme.controller", "com.acme.utils")).isEmpty();
        assertThat(violationsFor("com.acme.utils", "com.acme.repository")).isEmpty();
    }
}
