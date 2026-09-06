package com.arqsync.analyzer;

/**
 * A class in a package classified {@link PackageRole#DRIVEN_ADAPTER} that implements
 * no interface declared in a package classified {@link PackageRole#OUTPUT_PORT}, or a
 * class in a package classified {@link PackageRole#DRIVING_ADAPTER} that depends on
 * (via a field) no interface declared in a package classified
 * {@link PackageRole#INPUT_PORT} — it has no port either way, just via the opposite
 * obligation depending on direction (SPEC-adapter-port-violation.md, 3;
 * ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 2.4). {@code role} records which
 * obligation was checked, so the report and the AI suggestion prompt can phrase the
 * violation correctly ("doesn't implement" vs. "doesn't depend on") without
 * re-inspecting the package.
 */
public record AdapterSemPortaViolation(PackageName adapterPackage, String className, PackageRole role) {
}
