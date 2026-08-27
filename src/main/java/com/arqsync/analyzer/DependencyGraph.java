package com.arqsync.analyzer;

import java.util.List;
import java.util.Set;

/**
 * The package-level dependency graph (SPEC-analyzer.md, 2.1). Nodes are every
 * package in the scanned project; edges are the deduplicated internal imports.
 */
public record DependencyGraph(Set<PackageName> nodes, List<DependencyEdge> edges) {

    public DependencyGraph {
        nodes = Set.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public List<DependencyEdge> outgoingFrom(PackageName pkg) {
        return edges.stream()
                .filter(edge -> edge.from().equals(pkg))
                .toList();
    }

    public List<DependencyEdge> incomingTo(PackageName pkg) {
        return edges.stream()
                .filter(edge -> edge.to().equals(pkg))
                .toList();
    }
}
