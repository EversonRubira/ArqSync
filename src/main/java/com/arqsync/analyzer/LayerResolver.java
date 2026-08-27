package com.arqsync.analyzer;

import java.util.Locale;

/**
 * Resolves a package's {@link Layer} from its name (SPEC-analyzer.md, 2.4):
 * the rightmost dot-separated segment that matches a known layer name wins;
 * no match resolves to {@link Layer#UNKNOWN}. Package-private — an
 * implementation detail of the layer violation analysis, not part of the
 * Analyzer's public interface (SPEC-analyzer.md, section 4).
 */
final class LayerResolver {

    private LayerResolver() {
    }

    static Layer resolve(PackageName packageName) {
        String[] segments = packageName.value().split("\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            Layer layer = fromSegment(segments[i]);
            if (layer != Layer.UNKNOWN) {
                return layer;
            }
        }
        return Layer.UNKNOWN;
    }

    private static Layer fromSegment(String segment) {
        return switch (segment.toLowerCase(Locale.ROOT)) {
            case "controller" -> Layer.CONTROLLER;
            case "service" -> Layer.SERVICE;
            case "repository" -> Layer.REPOSITORY;
            case "domain" -> Layer.DOMAIN;
            default -> Layer.UNKNOWN;
        };
    }
}
