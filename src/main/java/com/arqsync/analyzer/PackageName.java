package com.arqsync.analyzer;

/**
 * A fully-qualified package name, the atomic node of the dependency graph
 * (SPEC-analyzer.md, 2.1).
 */
public record PackageName(String value) {
}
