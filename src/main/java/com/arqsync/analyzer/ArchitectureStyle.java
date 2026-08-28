package com.arqsync.analyzer;

/**
 * The architectural style inferred from a project's package names
 * (e.g. Layered, Hexagonal, Clean Architecture, DDD), with a short
 * didactic description of what that style means.
 */
public record ArchitectureStyle(String name, String description) {
}
