package com.arqsync.analyzer;

import java.util.List;

/**
 * A concrete dependency cycle. {@code path} is the ordered sequence of
 * packages in the cycle, with the first package repeated at the end
 * (e.g. [A, B, C, A]).
 */
public record Cycle(List<PackageName> path) {

    public Cycle {
        path = List.copyOf(path);
    }
}
