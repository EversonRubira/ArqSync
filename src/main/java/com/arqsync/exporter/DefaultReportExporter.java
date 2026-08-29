package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DefaultReportExporter implements ReportExporter {

    private static final Logger log = LoggerFactory.getLogger(DefaultReportExporter.class);

    private final JsonExporter jsonExporter;
    private final HtmlReportGenerator htmlReportGenerator;

    public DefaultReportExporter(JsonExporter jsonExporter, HtmlReportGenerator htmlReportGenerator) {
        this.jsonExporter = jsonExporter;
        this.htmlReportGenerator = htmlReportGenerator;
    }

    @Override
    public void export(ProjectScan projectScan, AnalysisResult analysisResult, Path outputDir, boolean generatePdf) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create output directory: " + outputDir, e);
        }

        Path jsonPath = jsonExporter.export(projectScan, analysisResult, outputDir);

        boolean htmlGenerated = htmlReportGenerator.generate(jsonPath, outputDir, generatePdf);
        if (!htmlGenerated) {
            log.warn("report.html was not generated; report.json is available at {}", jsonPath);
        }
    }

    @Override
    public void generatePdfOnly(Path outputDir) {
        Path jsonPath = outputDir.resolve("report.json");
        htmlReportGenerator.generate(jsonPath, outputDir, true);
    }
}
