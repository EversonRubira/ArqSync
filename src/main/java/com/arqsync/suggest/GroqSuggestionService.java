package com.arqsync.suggest;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;

import java.util.List;

/**
 * Requests architectural suggestions from the Groq API (--suggest, Fase 2).
 */
public interface GroqSuggestionService {

    /**
     * Never throws. Returns an empty list if {@code GROQ_API_KEY} isn't
     * configured, the API call fails (after one retry), or the response
     * can't be parsed — {@code --suggest} always degrades to "no AI
     * suggestions" rather than failing the pipeline. The returned list is
     * sorted by fixed severity: cycle-breaking suggestions first, then
     * layer-violation fixes, then architectural style guidance, then
     * anything else ({@link SuggestionType}'s declaration order).
     */
    List<AiSuggestion> suggest(ProjectScan projectScan, AnalysisResult analysisResult);
}
