package com.arqsync.cli;

/**
 * Abstraction over the interactive "Generate PDF report? (y/N)" prompt so
 * {@link ArqSyncPipelineRunner} can be tested without touching real console
 * input (SPEC-cli.md style, same reason {@link ProcessExiter} exists).
 */
public interface PdfConfirmationPrompt {

    /**
     * @return {@code true} if the user answered "y"/"Y"; {@code false} for
     * any other answer, including a non-interactive environment where there
     * is no one to ask (see {@link ConsolePdfConfirmationPrompt}).
     */
    boolean confirmPdfGeneration();
}
