package com.arqsync.cli;

import org.springframework.stereotype.Component;

import java.util.Scanner;

/**
 * Prompts "Generate PDF report? (y/N): " on stdout and reads the answer from
 * stdin via {@link Scanner}.
 *
 * <p>Guarded by {@link System#console()}: when there's no real interactive
 * terminal attached (CI, redirected/piped stdin, a test harness running the
 * jar) {@code console()} returns {@code null}, and this returns {@code false}
 * immediately instead of blocking on {@code Scanner#nextLine()} forever -
 * the same "never hang a non-interactive run" concern {@link ProcessExiter}
 * exists for, just for stdin instead of exit codes.
 */
@Component
public class ConsolePdfConfirmationPrompt implements PdfConfirmationPrompt {

    @Override
    public boolean confirmPdfGeneration() {
        if (System.console() == null) {
            return false;
        }

        System.out.print("Generate PDF report? (y/N): ");
        System.out.flush();

        Scanner scanner = new Scanner(System.in);
        if (!scanner.hasNextLine()) {
            return false;
        }
        String answer = scanner.nextLine().trim();
        return answer.equalsIgnoreCase("y");
    }
}
