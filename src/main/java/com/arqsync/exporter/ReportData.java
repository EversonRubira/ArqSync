package com.arqsync.exporter;

import com.arqsync.analyzer.AnalysisMetrics;
import com.arqsync.analyzer.ArchitectureStyle;
import com.arqsync.analyzer.Cycle;
import com.arqsync.analyzer.DependencyGraph;
import com.arqsync.analyzer.LayerViolation;
import com.arqsync.suggest.AiSuggestion;

import java.time.Instant;
import java.util.List;

/**
 * Dedicated serialization shape for {@code report.json} — not a raw dump of
 * {@code ProjectScan}/{@code AnalysisResult} (SPEC-exporter.md, 2.8). This,
 * not the Java domain models, is the contract the Python side of the
 * Exporter reads. {@code aiSuggestions} is empty unless {@code --suggest}
 * was passed and the Groq API returned suggestions (Fase 2).
 */
public record ReportData(
        String projectName,
        String rootPath,
        Instant generatedAt,
        AnalysisMetrics metrics,
        List<Cycle> cycles,
        List<LayerViolation> violations,
        DependencyGraph dependencyGraph,
        ArchitectureStyle architectureStyle,
        List<AiSuggestion> aiSuggestions
) {

    public ReportData {
        cycles = List.copyOf(cycles);
        violations = List.copyOf(violations);
        aiSuggestions = List.copyOf(aiSuggestions);
    }
}
