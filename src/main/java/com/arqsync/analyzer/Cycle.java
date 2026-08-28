package com.arqsync.analyzer;

import java.util.List;

/**
 * A concrete dependency cycle. {@code path} is the ordered sequence of
 * packages in the cycle, with the first package repeated at the end
 * (e.g. [A, B, C, A]). {@code explanation} and {@code suggestion} are
 * didactic text describing why the cycle is a problem and how to break it.
 */
public record Cycle(List<PackageName> path, String explanation, String suggestion) {

    public Cycle {
        path = List.copyOf(path);
    }
}
