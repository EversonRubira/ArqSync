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

    /**
     * Generates {@code report.pdf} for an output directory that was already
     * exported (i.e. {@code report.json} already exists there) - without
     * redoing the JSON export or the analysis. Used for the interactive
     * "Generate PDF report? (y/N)" follow-up: the answer isn't known until
     * after the initial export already ran without {@code --pdf}.
     */
    void generatePdfOnly(Path outputDir);
}
