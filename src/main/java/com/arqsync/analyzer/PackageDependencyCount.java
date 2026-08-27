package com.arqsync.analyzer;

/**
 * Incoming/outgoing dependency counts for a single package.
 */
public record PackageDependencyCount(PackageName pkg, int incoming, int outgoing) {
}
