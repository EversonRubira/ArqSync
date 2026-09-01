package com.arqsync.scanner;

import java.util.List;

/**
 * A single top-level type declaration (class, interface, enum, record, annotation)
 * found in a .java file, with the imports of the file it was declared in.
 *
 * <p>{@code superTypes} are the names declared in {@code implements}/{@code extends}
 * (ADENDO-SPEC-scanner-supertypes.md, 2.1) — simple names, not resolved to a package;
 * that resolution is the Analyzer's responsibility. {@code isInterface} is {@code true}
 * only for an actual {@code interface} declaration (not class/enum/record/annotation) —
 * needed to tell a "porta" (interface) apart from an adapter class that happens to
 * extend/implement something (same adendo, 2.1).
 */
public record ClassScan(String name, String packageName, List<String> imports,
                         List<String> superTypes, boolean isInterface) {

    public ClassScan {
        imports = List.copyOf(imports);
        superTypes = List.copyOf(superTypes);
    }
}
