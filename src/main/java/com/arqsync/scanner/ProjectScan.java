package com.arqsync.scanner;

import java.util.List;

/**
 * The raw structural result of scanning a Java project: every package found,
 * plus every file that could not be scanned. Consumed by the Analyzer.
 */
public record ProjectScan(String rootPath, List<PackageScan> packages, List<ScanError> errors) {

    public ProjectScan {
        packages = List.copyOf(packages);
        errors = List.copyOf(errors);
    }
}
