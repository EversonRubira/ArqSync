package com.arqsync.scanner;

/**
 * Represents a file that could not be scanned (parse or read failure).
 */
public record ScanError(String filePath, String message) {
}
