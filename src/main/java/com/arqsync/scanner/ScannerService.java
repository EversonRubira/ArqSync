package com.arqsync.scanner;

import java.nio.file.Path;

/**
 * Entry point of the Scanner component. Walks a directory of .java files
 * (no build tool required) and produces a {@link ProjectScan}.
 */
public interface ScannerService {

    /**
     * @throws InvalidProjectPathException if {@code path} does not exist or is not a directory
     */
    ProjectScan scan(Path path);
}
