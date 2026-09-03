package com.arqsync.suggest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiSuggestionTest {

    @Test
    void nullCodeExampleStaysNull() {
        AiSuggestion suggestion = new AiSuggestion(SuggestionType.GENERAL, "t", "d", null);

        assertThat(suggestion.codeExample()).isNull();
    }

    @Test
    void blankCodeExampleIsNormalizedToNull() {
        AiSuggestion suggestion = new AiSuggestion(SuggestionType.GENERAL, "t", "d", "   ");

        assertThat(suggestion.codeExample()).isNull();
    }

    @Test
    void literalStringNullIsNormalizedToNull() {
        // Some models return the text "null" as a real string value instead
        // of the JSON null literal or omitting the field - must be treated
        // the same as no example (see AiSuggestion's compact constructor).
        AiSuggestion suggestion = new AiSuggestion(SuggestionType.GENERAL, "t", "d", "null");

        assertThat(suggestion.codeExample()).isNull();
    }

    @Test
    void literalStringNullIsCaseInsensitiveAndTrimmed() {
        AiSuggestion suggestion = new AiSuggestion(SuggestionType.GENERAL, "t", "d", "  NULL  ");

        assertThat(suggestion.codeExample()).isNull();
    }

    @Test
    void realCodeExampleIsKeptAsIs() {
        AiSuggestion suggestion = new AiSuggestion(SuggestionType.GENERAL, "t", "d", "interface X {}");

        assertThat(suggestion.codeExample()).isEqualTo("interface X {}");
    }

    @Test
    void aWordThatMerelyContainsNullIsNotTreatedAsTheLiteral() {
        // Guards against an overly aggressive normalization (e.g. substring
        // matching instead of exact-match) rejecting a legitimate example
        // that happens to mention "null" as part of real code.
        AiSuggestion suggestion = new AiSuggestion(SuggestionType.GENERAL, "t", "d", "return null;");

        assertThat(suggestion.codeExample()).isEqualTo("return null;");
    }
}
