package com.arqsync.analyzer;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Implements only the Hexagonal heuristic today (the only one with a
 * consumer so far — {@code DefaultAdapterPortViolationDetector}). Clean
 * Architecture, DDD and {@code Layer.CROSS_CUTTING} are deferred until
 * SPEC-diagram-concept.md is implemented (ADENDO-SPEC-analyzer-classificador-papel.md,
 * 5) — every other style resolves every package to {@link PackageRole#UNKNOWN}.
 *
 * <p>Reuses the exact same segment keywords {@code DefaultArchitectureStyleDetector}
 * already uses to detect Hexagonal ({@code port}/{@code ports}, {@code adapter}/
 * {@code adapters}), applied per package instead of once for the whole project
 * (ADENDO, 2.1) — same rightmost-segment-wins convention as {@link LayerResolver}.
 */
@Component
public class DefaultPackageRoleClassifier implements PackageRoleClassifier {

    @Override
    public Map<PackageName, PackageRole> classify(DependencyGraph graph, ArchitectureStyle style) {
        Map<PackageName, PackageRole> roles = new LinkedHashMap<>();
        boolean hexagonal = DefaultArchitectureStyleDetector.HEXAGONAL.equals(style);

        for (PackageName pkg : graph.nodes()) {
            roles.put(pkg, hexagonal ? classifyHexagonal(pkg) : PackageRole.UNKNOWN);
        }
        return roles;
    }

    private PackageRole classifyHexagonal(PackageName packageName) {
        String[] segments = packageName.value().split("\\.");
        for (int i = segments.length - 1; i >= 0; i--) {
            PackageRole role = fromSegment(segments[i]);
            if (role != PackageRole.UNKNOWN) {
                return role;
            }
        }
        return PackageRole.UNKNOWN;
    }

    private PackageRole fromSegment(String segment) {
        return switch (segment.toLowerCase(Locale.ROOT)) {
            case "port", "ports" -> PackageRole.CORE;
            case "adapter", "adapters" -> PackageRole.ADAPTER;
            default -> PackageRole.UNKNOWN;
        };
    }
}
