package com.arqsync.exporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Invokes {@code scripts/generate-report.py} via {@link ProcessBuilder}
 * (SPEC-exporter.md, 2.3). Tries {@code python}, then {@code py}, then
 * {@code python3} (in that order) as fallbacks if one interpreter isn't
 * found. {@code python} is tried first because on Windows {@code python3}
 * commonly resolves to a Microsoft Store "app execution alias" stub instead
 * of a real interpreter when Python was installed via python.org - the stub
 * launches successfully (no {@link IOException}) but exits non-zero, so it
 * can't be told apart from a genuine failure and must be avoided as the
 * first candidate.
 */
@Component
public class DefaultHtmlReportGenerator implements HtmlReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultHtmlReportGenerator.class);
    static final List<String> DEFAULT_PYTHON_COMMANDS = List.of("python", "py", "python3");
    private static final String REPORT_HTML = "report.html";
    private static final String REPORT_PDF = "report.pdf";

    private final Path scriptPath;
    private final List<String> pythonCommands;

    public DefaultHtmlReportGenerator() {
        this(Path.of("scripts", "generate-report.py"), DEFAULT_PYTHON_COMMANDS);
    }

    /**
     * Visible for tests, to point at a fixture script and/or a fake interpreter
     * name (to simulate "Python not found" without touching the real PATH).
     */
    DefaultHtmlReportGenerator(Path scriptPath, List<String> pythonCommands) {
        this.scriptPath = scriptPath;
        this.pythonCommands = pythonCommands;
    }

    @Override
    public boolean generate(Path jsonPath, Path outputDir, boolean generatePdf) {
        log.debug("Generating report.html from {} into {} using script {} (interpreter candidates: {}, pdf: {})",
                jsonPath, outputDir, scriptPath, pythonCommands, generatePdf);
        for (String pythonCommand : pythonCommands) {
            Optional<Boolean> result = tryGenerate(pythonCommand, jsonPath, outputDir, generatePdf);
            if (result.isPresent()) {
                return result.get();
            }
            // interpreter not found under this name; try the next candidate
        }
        log.warn("Could not find a Python interpreter (tried: {}) - report.html was not generated", pythonCommands);
        return false;
    }

    private Optional<Boolean> tryGenerate(String pythonCommand, Path jsonPath, Path outputDir, boolean generatePdf) {
        List<String> command = new ArrayList<>(List.of(
                pythonCommand, scriptPath.toString(),
                "--json-path", jsonPath.toString(),
                "--output-dir", outputDir.toString()
        ));
        if (generatePdf) {
            command.add("--pdf");
        }
        log.debug("Attempting report.html generation with: {}", command);
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("generate-report.py failed using '{}' (exit code {}): {}",
                        pythonCommand, exitCode, stderr.strip());
                return Optional.of(false);
            }

            boolean htmlExists = Files.exists(outputDir.resolve(REPORT_HTML));
            if (!htmlExists) {
                log.error("generate-report.py exited successfully but {} was not found in {}", REPORT_HTML, outputDir);
            } else {
                log.debug("report.html generated successfully using '{}'", pythonCommand);
                // PDF generation is best-effort and doesn't affect this method's
                // return value (exit code stays 0 even when it's skipped) - but
                // that also means its stderr warning is never logged below, so
                // surface it explicitly here instead of silently dropping it.
                if (generatePdf && !Files.exists(outputDir.resolve(REPORT_PDF)) && !stderr.isBlank()) {
                    log.warn("report.pdf was requested but not generated: {}", stderr.strip());
                }
            }
            return Optional.of(htmlExists);
        } catch (IOException e) {
            // "<command> not found" - signal "try the next candidate", not "failed"
            log.debug("Python interpreter '{}' is not available ({}); trying next candidate", pythonCommand, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for generate-report.py", e);
            return Optional.of(false);
        }
    }
}
