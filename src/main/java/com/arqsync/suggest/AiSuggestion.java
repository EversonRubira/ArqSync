package com.arqsync.suggest;

/**
 * One suggestion produced by the Groq LLM from a structured summary of an
 * {@code AnalysisResult} — never from a refactoring the tool performed
 * itself (--suggest only ever suggests, it never rewrites project code).
 * {@code codeExample} is {@code null} when the AI didn't provide one.
 */
public record AiSuggestion(
        SuggestionType type,
        String title,
        String description,
        String codeExample
) {

    public AiSuggestion {
        codeExample = (codeExample == null || codeExample.isBlank()) ? null : codeExample;
    }
}
