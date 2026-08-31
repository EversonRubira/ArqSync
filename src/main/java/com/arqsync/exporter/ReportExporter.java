package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import com.arqsync.suggest.AiSuggestion;

import java.nio.file.Path;
import java.util.List;

public interface ReportExporter {

    /**
     * @param aiSuggestions AI-generated suggestions to include in the
     *                      generated reports (empty when {@code --suggest}
     *                      wasn't passed or yielded nothing).
     * @param generatePdf   whether to also ask the HTML report generator to
     *                      produce a {@code report.pdf} alongside {@code report.html}.
     */
    void export(ProjectScan projectScan, AnalysisResult analysisResult, List<AiSuggestion> aiSuggestions,
                Path outputDir, boolean generatePdf);
}
