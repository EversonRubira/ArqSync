package com.arqsync.scanner;

import java.nio.file.Path;

/**
 * Wraps the JavaParser library so that no parsing exception ever escapes —
 * every outcome is represented as a {@link ParseOutcome} value (SPEC-scanner.md, 2.2).
 */
public interface JavaParserAdapter {

    ParseOutcome parse(Path file);
}
