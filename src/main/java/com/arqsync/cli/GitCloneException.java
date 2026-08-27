package com.arqsync.cli;

/**
 * Fatal error while resolving a Git URL argument into a local directory to
 * analyze: malformed URL, private/inaccessible repository, unreachable
 * network, clone timeout, or a cloned repository that exceeds the v1 size
 * or file-count limits (SPEC-cli.md, "Entrada via URL").
 */
public class GitCloneException extends RuntimeException {

    public GitCloneException(String message) {
        super(message);
    }

    public GitCloneException(String message, Throwable cause) {
        super(message, cause);
    }
}
