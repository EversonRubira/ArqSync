package com.arqsync.exporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Invokes {@code scripts/generate-report.py} via {@link ProcessBuilder}
 * (SPEC-exporter.md, 2.3). Tries {@code python3} first, then {@code python}
 * as a fallback if the interpreter itself isn't found.
 */
@Component
public class DefaultHtmlReportGenerator implements HtmlReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(DefaultHtmlReportGenerator.class);
    private static final List<String> DEFAULT_PYTHON_COMMANDS = List.of("python3", "python");
    private static final String REPORT_HTML = "report.html";

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
    public boolean generate(Path jsonPath, Path outputDir) {
        for (String pythonCommand : pythonCommands) {
            Optional<Boolean> result = tryGenerate(pythonCommand, jsonPath, outputDir);
            if (result.isPresent()) {
                return result.get();
            }
            // interpreter not found under this name; try the next candidate
        }
        log.warn("Could not find a Python interpreter (tried: {}) - report.html was not generated", pythonCommands);
        return false;
    }

    private Optional<Boolean> tryGenerate(String pythonCommand, Path jsonPath, Path outputDir) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonCommand, scriptPath.toString(),
                "--json-path", jsonPath.toString(),
                "--output-dir", outputDir.toString()
        ).redirectOutput(ProcessBuilder.Redirect.DISCARD);

        try {
            Process process = processBuilder.start();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("generate-report.py failed (exit code {}): {}", exitCode, stderr.strip());
                return Optional.of(false);
            }

            boolean htmlExists = Files.exists(outputDir.resolve(REPORT_HTML));
            if (!htmlExists) {
                log.error("generate-report.py exited successfully but {} was not found in {}", REPORT_HTML, outputDir);
            }
            return Optional.of(htmlExists);
        } catch (IOException e) {
            // "<command> not found" - signal "try the next candidate", not "failed"
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for generate-report.py", e);
            return Optional.of(false);
        }
    }
}
