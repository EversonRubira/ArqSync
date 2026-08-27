package com.arqsync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point: {@code java -jar arqsync.jar <caminho-do-projeto>} (SPEC-cli.md).
 * The actual pipeline orchestration lives in {@link com.arqsync.cli.ArqSyncPipelineRunner}.
 */
@SpringBootApplication
public class ArqSyncApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(ArqSyncApplication.class, args);
        } catch (Exception e) {
            // Spring context failed to start (e.g. the database is unreachable, which
            // fails Flyway's startup migration - see STATUS.md for the known gap this
            // doesn't fully resolve). A short message beats a huge stack trace here,
            // in the same spirit as ArqSyncPipelineRunner's clean fatal-error logging
            // (SPEC-cli.md, 2.9), even though the pipeline itself never got to run.
            System.err.println("ERROR: ArqSync failed to start: " + rootCauseMessage(e));
            System.exit(1);
        }
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
