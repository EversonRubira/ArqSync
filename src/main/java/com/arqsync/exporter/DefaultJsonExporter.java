package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import com.arqsync.suggest.AiSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Component
public class DefaultJsonExporter implements JsonExporter {

    private final ObjectMapper objectMapper;

    public DefaultJsonExporter() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Path export(ProjectScan projectScan, AnalysisResult analysisResult, List<AiSuggestion> aiSuggestions, Path outputDir) {
        ReportData reportData = toReportData(projectScan, analysisResult, aiSuggestions);
        Path jsonPath = outputDir.resolve("report.json");
        try {
            objectMapper.writeValue(jsonPath.toFile(), reportData);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write report.json to " + jsonPath, e);
        }
        return jsonPath;
    }

    private ReportData toReportData(ProjectScan projectScan, AnalysisResult analysisResult, List<AiSuggestion> aiSuggestions) {
        return new ReportData(
                projectNameFrom(projectScan.rootPath()),
                projectScan.rootPath(),
                Instant.now(),
                analysisResult.metrics(),
                analysisResult.cycles(),
                analysisResult.violations(),
                analysisResult.adapterPortViolations(),
                analysisResult.dependencyGraph(),
                analysisResult.architectureStyle(),
                aiSuggestions
        );
    }

    private String projectNameFrom(String path) {
        String normalized = (path.endsWith("/") || path.endsWith("\\"))
                ? path.substring(0, path.length() - 1)
                : path;
        int lastSlash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        return lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
    }
}
