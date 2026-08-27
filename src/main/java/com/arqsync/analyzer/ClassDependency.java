package com.arqsync.analyzer;

/**
 * One concrete class-to-class import that contributed to a {@link DependencyEdge}.
 */
public record ClassDependency(String fromClass, String toClass) {
}
