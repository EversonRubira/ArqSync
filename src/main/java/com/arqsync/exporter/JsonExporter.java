package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import com.arqsync.suggest.AiSuggestion;

import java.nio.file.Path;
import java.util.List;

public interface JsonExporter {

    /**
     * Writes {@code report.json} to {@code outputDir} and returns its path.
     * {@code aiSuggestions} is empty when {@code --suggest} wasn't passed or
     * the Groq API didn't return any suggestions.
     */
    Path export(ProjectScan projectScan, AnalysisResult analysisResult, List<AiSuggestion> aiSuggestions, Path outputDir);
}
