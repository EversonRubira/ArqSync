package com.arqsync.analyzer;

import java.util.List;

/**
 * A single layer-convention violation found on one edge of the dependency graph.
 */
public record LayerViolation(
        PackageName from,
        PackageName to,
        Layer fromLayer,
        Layer toLayer,
        ViolationType type,
        List<ClassDependency> classSamples,
        String explanation
) {

    public LayerViolation {
        classSamples = List.copyOf(classSamples);
    }
}
