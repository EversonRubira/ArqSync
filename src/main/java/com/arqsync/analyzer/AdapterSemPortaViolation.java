package com.arqsync.analyzer;

/**
 * A class in a package classified as {@link PackageRole#ADAPTER} (Hexagonal
 * style only) that implements no interface declared in a package classified
 * as {@link PackageRole#CORE} — it has no port (SPEC-adapter-port-violation.md, 3).
 */
public record AdapterSemPortaViolation(PackageName adapterPackage, String className) {
}
