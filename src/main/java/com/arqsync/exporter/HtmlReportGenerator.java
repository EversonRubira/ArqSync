package com.arqsync.exporter;

import java.nio.file.Path;

public interface HtmlReportGenerator {

    /**
     * Invokes the Python script. Returns {@code true} on success, {@code false}
     * on any failure — never throws (SPEC-exporter.md, 2.3).
     */
    boolean generate(Path jsonPath, Path outputDir);
}
