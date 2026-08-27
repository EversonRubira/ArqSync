package com.arqsync.analyzer;

import java.util.List;

/**
 * A deduplicated directed edge between two packages, with a bounded sample of
 * the class pairs that produced it (SPEC-analyzer.md, 2.2).
 */
public record DependencyEdge(
        PackageName from,
        PackageName to,
        int occurrences,
        List<ClassDependency> classSamples
) {

    public DependencyEdge {
        classSamples = List.copyOf(classSamples);
    }
}
