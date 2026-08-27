package com.arqsync.scanner;

import java.util.List;

/**
 * All classes found under a single fully-qualified package name.
 */
public record PackageScan(String name, List<ClassScan> classes) {

    public PackageScan {
        classes = List.copyOf(classes);
    }
}
