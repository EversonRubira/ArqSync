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
 *
 * <p>{@code fieldTypes} are the declared types (simple names) of the type's non-static
 * instance fields (ADENDO-SPEC-analyzer-adapter-porta-direcao.md, 2.5) — needed to tell
 * whether a driving adapter *depends on* a port, since a driving adapter's obligation is
 * the opposite of a driven adapter's: depend on a port, not implement it. Field types,
 * not constructor parameter types, because Lombok's {@code @RequiredArgsConstructor}
 * (idiomatic in real Spring projects) generates the constructor via annotation
 * processing — it never appears as a node in the AST this Scanner parses, so scanning
 * constructor parameters would silently miss every Lombok-based driving adapter. The
 * field declaration itself is always present in source regardless of how the
 * constructor came to exist.
 */
public record ClassScan(String name, String packageName, List<String> imports,
                         List<String> superTypes, boolean isInterface,
                         List<String> fieldTypes) {

    public ClassScan {
        imports = List.copyOf(imports);
        superTypes = List.copyOf(superTypes);
        fieldTypes = List.copyOf(fieldTypes);
    }
}
