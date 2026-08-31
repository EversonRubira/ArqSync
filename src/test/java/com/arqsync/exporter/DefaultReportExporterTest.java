package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.ArchitectureStyle;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.scanner.ProjectScan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DefaultReportExporterTest {

    private ProjectScan projectScan() {
        return new ProjectScan("/repo/my-project", List.of(), List.of());
    }

    private AnalysisResult emptyAnalysisResult() {
        return new AnalysisResult(
                new DependencyGraph(Set.of(), List.of()),
                List.of(),
                List.of(),
                new AnalysisMetrics(0, 0, 0, 0, List.of()),
                new ArchitectureStyle("Não identificado", "")
        );
    }

    private Path realScriptPath() throws URISyntaxException {
        // scripts/generate-report.py at the repository root, relative to the
        // Maven working directory (same assumption DefaultHtmlReportGenerator's
        // production default makes).
        return Paths.get("scripts", "generate-report.py");
    }

    private Path fixtureScript(String name) throws URISyntaxException {
        return Paths.get(
                Objects.requireNonNull(
                        getClass().getClassLoader().getResource("fixtures/exporter/scripts/" + name)
                ).toURI()
        );
    }

    @Test
    void pythonAvailableAndScriptSucceedsProducesBothArtifacts(@TempDir Path outputDir) throws URISyntaxException {
        HtmlReportGenerator htmlReportGenerator =
                new DefaultHtmlReportGenerator(realScriptPath(), DefaultHtmlReportGenerator.DEFAULT_PYTHON_COMMANDS);
        ReportExporter exporter = new DefaultReportExporter(new DefaultJsonExporter(), htmlReportGenerator);

        exporter.export(projectScan(), emptyAnalysisResult(), List.of(), outputDir, false);

        assertThat(outputDir.resolve("report.json")).exists();
        assertThat(outputDir.resolve("report.html")).exists();
    }

    @Test
    void pythonUnavailableStillProducesJsonAndDoesNotThrow(@TempDir Path outputDir) throws URISyntaxException {
        HtmlReportGenerator htmlReportGenerator =
                new DefaultHtmlReportGenerator(realScriptPath(), List.of("no-such-python-interpreter"));
        ReportExporter exporter = new DefaultReportExporter(new DefaultJsonExporter(), htmlReportGenerator);

        assertThatCode(() -> exporter.export(projectScan(), emptyAnalysisResult(), List.of(), outputDir, false))
                .doesNotThrowAnyException();

        assertThat(outputDir.resolve("report.json")).exists();
        assertThat(outputDir.resolve("report.html")).doesNotExist();
    }

    @Test
    void scriptExitingWithNonZeroCodeStillProducesJsonAndDoesNotThrow(@TempDir Path outputDir)
            throws URISyntaxException {
        HtmlReportGenerator htmlReportGenerator =
                new DefaultHtmlReportGenerator(fixtureScript("always-fails.py"), DefaultHtmlReportGenerator.DEFAULT_PYTHON_COMMANDS);
        ReportExporter exporter = new DefaultReportExporter(new DefaultJsonExporter(), htmlReportGenerator);

        assertThatCode(() -> exporter.export(projectScan(), emptyAnalysisResult(), List.of(), outputDir, false))
                .doesNotThrowAnyException();

        assertThat(outputDir.resolve("report.json")).exists();
        assertThat(outputDir.resolve("report.html")).doesNotExist();
    }

    @Test
    void createsOutputDirWhenItDoesNotExistYet(@TempDir Path tempDir) throws URISyntaxException {
        Path outputDir = tempDir.resolve("nested/does/not/exist/yet");
        HtmlReportGenerator htmlReportGenerator =
                new DefaultHtmlReportGenerator(realScriptPath(), List.of("no-such-python-interpreter"));
        ReportExporter exporter = new DefaultReportExporter(new DefaultJsonExporter(), htmlReportGenerator);

        exporter.export(projectScan(), emptyAnalysisResult(), List.of(), outputDir, false);

        assertThat(Files.isDirectory(outputDir)).isTrue();
        assertThat(outputDir.resolve("report.json")).exists();
    }

    @Test
    void generatePdfRequestedStillProducesHtmlAndNeverThrowsEvenIfThePdfLibraryIsMissing(@TempDir Path outputDir)
            throws URISyntaxException {
        // PDF generation is opt-in and best-effort (SPEC-exporter.md-style graceful
        // degradation, same as "Python not found" for report.html): whether or not
        // the optional PDF library is installed in this environment, report.html
        // must still be produced and export() must never throw.
        HtmlReportGenerator htmlReportGenerator =
                new DefaultHtmlReportGenerator(realScriptPath(), DefaultHtmlReportGenerator.DEFAULT_PYTHON_COMMANDS);
        ReportExporter exporter = new DefaultReportExporter(new DefaultJsonExporter(), htmlReportGenerator);

        assertThatCode(() -> exporter.export(projectScan(), emptyAnalysisResult(), List.of(), outputDir, true))
                .doesNotThrowAnyException();

        assertThat(outputDir.resolve("report.json")).exists();
        assertThat(outputDir.resolve("report.html")).exists();
    }
}
