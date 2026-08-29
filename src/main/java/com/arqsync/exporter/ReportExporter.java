package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;

import java.nio.file.Path;

public interface ReportExporter {

    /**
     * @param generatePdf whether to also ask the HTML report generator to
     *                    produce a {@code report.pdf} alongside {@code report.html}.
     */
    void export(ProjectScan projectScan, AnalysisResult analysisResult, Path outputDir, boolean generatePdf);
}
