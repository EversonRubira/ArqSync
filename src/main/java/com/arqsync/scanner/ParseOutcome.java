package com.arqsync.scanner;

import java.util.List;

/**
 * The result of attempting to parse a single .java file. Never an exception —
 * a parse failure is an expected, modeled outcome (see SPEC-scanner.md, 2.2),
 * not a control-flow exception.
 */
public sealed interface ParseOutcome permits ParseOutcome.Success, ParseOutcome.Failure {

    record Success(List<ClassScan> classes) implements ParseOutcome {

        public Success {
            classes = List.copyOf(classes);
        }
    }

    record Failure(ScanError error) implements ParseOutcome {
    }
}
