package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;

import java.nio.file.Path;

public interface JsonExporter {

    /**
     * Writes {@code report.json} to {@code outputDir} and returns its path.
     */
    Path export(ProjectScan projectScan, AnalysisResult analysisResult, Path outputDir);
}
