package com.arqsync.scanner;

/**
 * Thrown when the path given to {@link ScannerService#scan(java.nio.file.Path)}
 * does not exist or is not a directory. This is a precondition failure on the
 * whole scan, not a per-file resilience concern.
 */
public class InvalidProjectPathException extends RuntimeException {

    public InvalidProjectPathException(String message) {
        super(message);
    }
}
