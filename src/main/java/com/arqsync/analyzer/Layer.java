package com.arqsync.analyzer;

/**
 * The architectural layer of a package, inferred from its name (SPEC-analyzer.md, 2.4).
 * Declaration order matters: it is used as the layer's rank when evaluating
 * layer violation rules (SPEC-analyzer.md, 2.3).
 */
public enum Layer {
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    DOMAIN,
    UNKNOWN
}
