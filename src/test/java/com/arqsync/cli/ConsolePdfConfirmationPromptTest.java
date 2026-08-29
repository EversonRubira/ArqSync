package com.arqsync.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolePdfConfirmationPromptTest {

    private final PdfConfirmationPrompt prompt = new ConsolePdfConfirmationPrompt();

    @Test
    void returnsFalseWithoutBlockingWhenThereIsNoRealConsole() {
        // Surefire/Failsafe never attach a real console to the test JVM, so
        // System.console() is reliably null here - this is exactly the
        // scenario the guard exists for (never hang ./mvnw test on stdin).
        assertThat(System.console()).isNull();

        assertThat(prompt.confirmPdfGeneration()).isFalse();
    }
}
