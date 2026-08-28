package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.ArchitectureStyle;
import com.arqsync.analyzer.Cycle;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.analyzer.Layer;
import com.arqsync.analyzer.LayerViolation;
import com.arqsync.analyzer.PackageDependencyCount;
import com.arqsync.analyzer.PackageName;
import com.arqsync.analyzer.ViolationType;
import com.arqsync.scanner.ProjectScan;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultJsonExporterTest {

    private final JsonExporter jsonExporter = new DefaultJsonExporter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProjectScan projectScan() {
        return new ProjectScan("/repo/my-project", List.of(), List.of());
    }

    private AnalysisResult analysisResultWithCycleAndViolation() {
        DependencyGraph graph = new DependencyGraph(
                Set.of(new PackageName("com.acme.controller"), new PackageName("com.acme.repository")),
                List.of()
        );
        List<Cycle> cycles = List.of(new Cycle(
                List.of(new PackageName("com.acme.a"), new PackageName("com.acme.b"), new PackageName("com.acme.a")),
                "com.acme.a e com.acme.b dependem um do outro, formando um ciclo.",
                "Extraia a responsabilidade compartilhada para um novo pacote."
        ));
        List<LayerViolation> violations = List.of(new LayerViolation(
                new PackageName("com.acme.controller"),
                new PackageName("com.acme.repository"),
                Layer.CONTROLLER,
                Layer.REPOSITORY,
                ViolationType.LAYER_SKIP,
                List.of(),
                "OrderController depende diretamente de OrderRepository, pulando a camada de service.",
                "Introduza a chamada através de service em vez de acessar repository diretamente."
        ));
        AnalysisMetrics metrics = new AnalysisMetrics(
                2, 5, 1, 1,
                List.of(new PackageDependencyCount(new PackageName("com.acme.controller"), 0, 1))
        );
        ArchitectureStyle architectureStyle = new ArchitectureStyle(
                "Arquitetura em Camadas (Layered)", "Descrição de teste."
        );
        return new AnalysisResult(graph, cycles, violations, metrics, architectureStyle);
    }

    private AnalysisResult emptyAnalysisResult() {
        return new AnalysisResult(
                new DependencyGraph(Set.of(), List.of()),
                List.of(),
                List.of(),
                new AnalysisMetrics(0, 0, 0, 0, List.of()),
                new ArchitectureStyle("Não identificado", "Descrição de teste.")
        );
    }

    @Test
    void writesReportJsonWithMetricsCyclesViolationsAndDependencyCounts(@TempDir Path outputDir) throws IOException {
        Path jsonPath = jsonExporter.export(projectScan(), analysisResultWithCycleAndViolation(), outputDir);

        assertThat(jsonPath).isEqualTo(outputDir.resolve("report.json"));
        assertThat(Files.exists(jsonPath)).isTrue();

        JsonNode root = objectMapper.readTree(jsonPath.toFile());
        assertThat(root.get("projectName").asText()).isEqualTo("my-project");
        assertThat(root.get("rootPath").asText()).isEqualTo("/repo/my-project");
        assertThat(root.get("metrics").get("totalPackages").asInt()).isEqualTo(2);
        assertThat(root.get("cycles")).hasSize(1);
        assertThat(root.get("cycles").get(0).get("path")).hasSize(3);
        assertThat(root.get("violations")).hasSize(1);
        assertThat(root.get("violations").get(0).get("explanation").asText())
                .contains("pulando a camada de service");
        assertThat(root.get("violations").get(0).get("suggestion").asText()).isNotBlank();
        assertThat(root.get("cycles").get(0).get("explanation").asText()).isNotBlank();
        assertThat(root.get("cycles").get(0).get("suggestion").asText()).isNotBlank();
        assertThat(root.get("architectureStyle").get("name").asText()).isEqualTo("Arquitetura em Camadas (Layered)");
        assertThat(root.get("metrics").get("dependencyCounts")).hasSize(1);
    }

    @Test
    void resultWithoutCyclesOrViolationsProducesEmptyArraysWithoutError(@TempDir Path outputDir) throws IOException {
        Path jsonPath = jsonExporter.export(projectScan(), emptyAnalysisResult(), outputDir);

        JsonNode root = objectMapper.readTree(jsonPath.toFile());
        assertThat(root.get("cycles")).isEmpty();
        assertThat(root.get("violations")).isEmpty();
    }

    @Test
    void generatedAtIsPresentAndCloseToNow(@TempDir Path outputDir) throws IOException {
        Instant before = Instant.now();
        Path jsonPath = jsonExporter.export(projectScan(), emptyAnalysisResult(), outputDir);
        Instant after = Instant.now();

        JsonNode root = objectMapper.readTree(jsonPath.toFile());
        Instant generatedAt = Instant.parse(root.get("generatedAt").asText());

        assertThat(generatedAt).isBetween(before, after);
    }

    @Test
    void writesTheFileExactlyAtOutputDirReportJson(@TempDir Path outputDir) {
        Path jsonPath = jsonExporter.export(projectScan(), emptyAnalysisResult(), outputDir);

        assertThat(jsonPath.getParent()).isEqualTo(outputDir);
        assertThat(jsonPath.getFileName().toString()).isEqualTo("report.json");
    }
}
