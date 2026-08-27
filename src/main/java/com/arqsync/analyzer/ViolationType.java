package com.arqsync.analyzer;

/**
 * The kind of layer violation (SPEC-analyzer.md, 2.3).
 */
public enum ViolationType {
    /** Depends on a layer further down than the immediate next one, skipping over an intermediate layer. */
    LAYER_SKIP,
    /** Depends on a layer that should depend on the source instead — the dependency direction is reversed. */
    LAYER_INVERSION
}
