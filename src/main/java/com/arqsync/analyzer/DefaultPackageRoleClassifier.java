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
 * (ADENDO, 2.1), plus {@code usecase}/{@code usecases} for input ports and
 * {@code in}/{@code driving}/{@code out}/{@code driven} for adapter direction
 * (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 2.2–2.3).
 *
 * <p>Unlike the original single-pass "rightmost segment wins" algorithm, this walk
 * doesn't stop at the first recognized segment: a package can need two independent
 * signals from two different segments — e.g. {@code adapters.in.web.controller} has
 * its role signal ({@code adapters}) three segments away from its direction signal
 * ({@code in}), and {@code adapters.out.mongo.adapter} has its role signal at the
 * rightmost segment with its direction signal two positions to the left of it. A
 * strict adjacency check would miss both — real package layouts observed via
 * dogfooding against a real Hexagonal project (see the adendo above).
 */
@Component
public class DefaultPackageRoleClassifier implements PackageRoleClassifier {

    private enum RoleSignal {
        INPUT_PORT, OUTPUT_PORT, ADAPTER
    }

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
        RoleSignal roleSignal = null;
        boolean driving = false;
        boolean driven = false;

        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i].toLowerCase(Locale.ROOT);
            if (isDataOrTranslationSegment(segment)) {
                // Cuts the search short (ADENDO-SPEC-analyzer-adapter-porta-direcao.md,
                // 4) even if an "adapter"/"port" segment sits further left (closer to
                // the root) — a dto/mapper/exception/document subpackage is never a
                // behavioral adapter, regardless of what package it lives inside.
                return PackageRole.UNKNOWN;
            }
            if (roleSignal == null) {
                roleSignal = roleSignalFrom(segment);
            }
            if (isDrivingDirection(segment)) {
                driving = true;
            }
            if (isDrivenDirection(segment)) {
                driven = true;
            }
        }

        if (roleSignal == null) {
            return PackageRole.UNKNOWN;
        }
        return switch (roleSignal) {
            case INPUT_PORT -> PackageRole.INPUT_PORT;
            case OUTPUT_PORT -> PackageRole.OUTPUT_PORT;
            case ADAPTER -> resolveAdapterDirection(driving, driven);
        };
    }

    private PackageRole resolveAdapterDirection(boolean driving, boolean driven) {
        if (driving && !driven) {
            return PackageRole.DRIVING_ADAPTER;
        }
        if (driven && !driving) {
            return PackageRole.DRIVEN_ADAPTER;
        }
        // Neither signal, or both (contradictory) - direction can't be trusted.
        return PackageRole.ADAPTER;
    }

    private boolean isDataOrTranslationSegment(String segment) {
        return switch (segment) {
            case "dto", "dtos", "mapper", "mappers", "exception", "exceptions",
                    "document", "documents" -> true;
            default -> false;
        };
    }

    private RoleSignal roleSignalFrom(String segment) {
        return switch (segment) {
            case "port", "ports" -> RoleSignal.OUTPUT_PORT;
            case "usecase", "usecases" -> RoleSignal.INPUT_PORT;
            case "adapter", "adapters" -> RoleSignal.ADAPTER;
            default -> null;
        };
    }

    private boolean isDrivingDirection(String segment) {
        return segment.equals("in") || segment.equals("driving");
    }

    private boolean isDrivenDirection(String segment) {
        return segment.equals("out") || segment.equals("driven");
    }
}
