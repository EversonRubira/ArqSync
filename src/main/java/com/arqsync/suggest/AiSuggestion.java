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
        codeExample = normalize(codeExample);
    }

    /**
     * Some models return the literal text {@code "null"} (a real, non-empty
     * JSON string) instead of the JSON {@code null} value or omitting the
     * field, despite the system prompt asking them not to — the schema
     * example in the prompt necessarily has to spell the word "null"
     * somewhere, and the model sometimes echoes it back verbatim as a
     * string value. Treated the same as an absent example either way.
     */
    private static String normalize(String codeExample) {
        if (codeExample == null || codeExample.isBlank()) {
            return null;
        }
        return codeExample.trim().equalsIgnoreCase("null") ? null : codeExample;
    }
}
