package com.arqsync.analyzer;

import java.util.Map;

/**
 * Classifies every package in the graph into a {@link PackageRole}, per the
 * detected {@link ArchitectureStyle} (ADENDO-SPEC-analyzer-classificador-papel.md).
 * Shared between the Adapter-sem-Porta violation rule and the (not yet
 * implemented) conceptual diagram — neither owns it.
 */
public interface PackageRoleClassifier {

    Map<PackageName, PackageRole> classify(DependencyGraph graph, ArchitectureStyle style);
}
