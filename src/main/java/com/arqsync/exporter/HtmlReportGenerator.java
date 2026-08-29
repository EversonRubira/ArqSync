package com.arqsync.exporter;

import java.nio.file.Path;

public interface HtmlReportGenerator {

    /**
     * Invokes the Python script. Returns {@code true} on success (report.html
     * was generated), {@code false} on any failure — never throws
     * (SPEC-exporter.md, 2.3). {@code generatePdf} additionally requests
     * {@code report.pdf}; PDF generation failing (e.g. the optional PDF
     * library isn't installed) does not affect this method's return value -
     * it's reported as a warning by the script itself, same as report.html
     * failing to render never fails the whole pipeline.
     */
    boolean generate(Path jsonPath, Path outputDir, boolean generatePdf);
}
