package com.arqsync.analyzer;

import com.arqsync.scanner.ClassScan;
import com.arqsync.scanner.PackageScan;
import com.arqsync.scanner.ProjectScan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the package-level {@link DependencyGraph} by resolving each class's
 * imports to a candidate package and keeping only edges internal to the
 * project (SPEC-analyzer.md, 2.6).
 */
@Component
public class DefaultDependencyGraphBuilder implements DependencyGraphBuilder {

    private static final int MAX_CLASS_SAMPLES = 5;
    private static final String STATIC_PREFIX = "static ";

    @Override
    public DependencyGraph build(ProjectScan projectScan) {
        Set<PackageName> internalPackages = new LinkedHashSet<>();
        for (PackageScan pkg : projectScan.packages()) {
            internalPackages.add(new PackageName(pkg.name()));
        }

        Map<EdgeKey, EdgeAccumulator> accumulators = new LinkedHashMap<>();

        for (PackageScan pkg : projectScan.packages()) {
            PackageName from = new PackageName(pkg.name());
            for (ClassScan cls : pkg.classes()) {
                for (String rawImport : cls.imports()) {
                    String candidatePackage = resolveCandidatePackage(rawImport);
                    if (candidatePackage.equals(pkg.name())) {
                        continue; // self-import
                    }

                    PackageName to = new PackageName(candidatePackage);
                    if (!internalPackages.contains(to)) {
                        continue; // external to the project
                    }

                    String importedTypeName = resolveImportedTypeName(rawImport);
                    accumulators
                            .computeIfAbsent(new EdgeKey(from, to), key -> new EdgeAccumulator())
                            .record(cls.name(), importedTypeName);
                }
            }
        }

        List<DependencyEdge> edges = accumulators.entrySet().stream()
                .map(entry -> new DependencyEdge(
                        entry.getKey().from(),
                        entry.getKey().to(),
                        entry.getValue().occurrences,
                        entry.getValue().samples()
                ))
                .toList();

        return new DependencyGraph(internalPackages, edges);
    }

    /**
     * Resolves an import's candidate package by dropping the trailing segment(s)
     * that name a type (and, for static imports, the member as well).
     */
    private String resolveCandidatePackage(String rawImport) {
        String[] segments = splitSegments(rawImport);
        int segmentsToDrop = isStatic(rawImport) ? 2 : 1;
        if (segments.length <= segmentsToDrop) {
            return "";
        }
        return String.join(".", List.of(segments).subList(0, segments.length - segmentsToDrop));
    }

    /**
     * Resolves the simple type name the import refers to, for use as a
     * {@link ClassDependency} sample — the class itself for a regular import,
     * the enclosing class for a static import, or "*" for a wildcard import.
     */
    private String resolveImportedTypeName(String rawImport) {
        String[] segments = splitSegments(rawImport);
        int typeIndex = isStatic(rawImport) ? segments.length - 2 : segments.length - 1;
        if (typeIndex < 0 || typeIndex >= segments.length) {
            return segments[segments.length - 1];
        }
        return segments[typeIndex];
    }

    private boolean isStatic(String rawImport) {
        return rawImport.startsWith(STATIC_PREFIX);
    }

    private String[] splitSegments(String rawImport) {
        String name = isStatic(rawImport) ? rawImport.substring(STATIC_PREFIX.length()) : rawImport;
        return name.split("\\.");
    }

    private record EdgeKey(PackageName from, PackageName to) {
    }

    private static final class EdgeAccumulator {
        private int occurrences = 0;
        private final List<ClassDependency> samples = new ArrayList<>();

        void record(String fromClass, String toClass) {
            occurrences++;
            if (samples.size() < MAX_CLASS_SAMPLES) {
                samples.add(new ClassDependency(fromClass, toClass));
            }
        }

        List<ClassDependency> samples() {
            return List.copyOf(samples);
        }
    }
}
