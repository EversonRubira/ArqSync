package com.arqsync.scanner;

import java.util.List;

/**
 * A single top-level type declaration (class, interface, enum, record, annotation)
 * found in a .java file, with the imports of the file it was declared in.
 */
public record ClassScan(String name, String packageName, List<String> imports) {

    public ClassScan {
        imports = List.copyOf(imports);
    }
}
