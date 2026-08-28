package com.arqsync.analyzer;

/**
 * Infers the architectural style a project is following from its package
 * names (SPEC-analyzer.md conventions extended to more than the fixed
 * layer names, for reporting purposes only — has no effect on cycle or
 * layer-violation detection).
 */
public interface ArchitectureStyleDetector {

    ArchitectureStyle detect(DependencyGraph graph);
}
