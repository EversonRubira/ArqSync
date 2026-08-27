package com.arqsync.analyzer;

import com.arqsync.scanner.ClassScan;
import com.arqsync.scanner.PackageScan;
import com.arqsync.scanner.ProjectScan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder-style helper for constructing {@link ProjectScan} fixtures in
 * memory, without any file I/O (SPEC-analyzer.md, seção 6).
 */
final class ProjectScanFixtures {

    private ProjectScanFixtures() {
    }

    static ProjectScan empty() {
        return new ProjectScan("/fixture", List.of(), List.of());
    }

    /**
     * A package per given name, each with one class importing the next
     * package's class, and the last one importing back to the first —
     * a simple cyclic chain. {@code withCycle("a.controller")} alone
     * produces a self-loop.
     */
    static ProjectScan withCycle(String... packageNames) {
        Builder builder = builder();
        for (int i = 0; i < packageNames.length; i++) {
            String from = packageNames[i];
            String to = packageNames[(i + 1) % packageNames.length];
            builder.classImporting(from, classNameFor(from), to, classNameFor(to));
        }
        return builder.build();
    }

    static String classNameFor(String packageName) {
        String[] segments = packageName.split("\\.");
        String last = segments[segments.length - 1];
        return Character.toUpperCase(last.charAt(0)) + last.substring(1);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private final Map<String, List<ClassScan>> classesByPackage = new LinkedHashMap<>();

        /**
         * Adds a class in {@code fromPackage} importing the given class from
         * {@code toPackage}, and ensures {@code toPackage} itself exists as a
         * package in the fixture — an import only becomes a graph edge if its
         * candidate package is present in {@code ProjectScan.packages()}
         * (SPEC-analyzer.md, 2.6), exactly as the real Scanner would only emit
         * a PackageScan for a package that has at least one class in it.
         */
        Builder classImporting(String fromPackage, String fromClass, String toPackage, String toClass) {
            addClass(fromPackage, fromClass, List.of(toPackage + "." + toClass));
            emptyPackage(toPackage);
            return this;
        }

        /** Adds a class with an arbitrary, explicit list of raw import strings. */
        Builder classWithImports(String packageName, String className, String... imports) {
            addClass(packageName, className, List.of(imports));
            return this;
        }

        /** Ensures a package exists even if it ends up with no classes added otherwise. */
        Builder emptyPackage(String packageName) {
            classesByPackage.computeIfAbsent(packageName, key -> new ArrayList<>());
            return this;
        }

        private void addClass(String packageName, String className, List<String> imports) {
            classesByPackage
                    .computeIfAbsent(packageName, key -> new ArrayList<>())
                    .add(new ClassScan(className, packageName, imports));
        }

        ProjectScan build() {
            List<PackageScan> packages = classesByPackage.entrySet().stream()
                    .map(entry -> new PackageScan(entry.getKey(), entry.getValue()))
                    .toList();
            return new ProjectScan("/fixture", packages, List.of());
        }
    }
}
