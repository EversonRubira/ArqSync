package com.arqsync.analyzer;

/**
 * The architectural role of a package under a style-specific classifier
 * (ADENDO-SPEC-analyzer-classificador-papel.md) — e.g. for Hexagonal, whether
 * a package holds core/port interfaces or adapter implementations.
 * {@code UNKNOWN} means the classifier found no signal for that package, the
 * same "not a miss" philosophy as {@link ArchitectureStyle}'s own UNKNOWN
 * (SPEC-analyzer.md, 2.4).
 */
public enum PackageRole {
    CORE,
    ADAPTER,
    UNKNOWN
}
