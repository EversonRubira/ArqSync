package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;

import java.nio.file.Path;

public interface ReportExporter {

    void export(ProjectScan projectScan, AnalysisResult analysisResult, Path outputDir);
}
