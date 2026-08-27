package com.arqsync.cli;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.DependencyAnalyzer;
import com.arqsync.exporter.ReportExporter;
import com.arqsync.persistence.PersistenceService;
import com.arqsync.scanner.InvalidProjectPathException;
import com.arqsync.scanner.ProjectScan;
import com.arqsync.scanner.ScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Orchestrates Scanner -> Analyzer -> Persistence -> Exporter (SPEC-cli.md).
 *
 * <p>Named {@code ArqSyncPipelineRunner}, not {@code CommandLineRunner} as the
 * Spec's interface section literally shows — that name collides with
 * {@link org.springframework.boot.CommandLineRunner}, an unrelated Spring Boot
 * interface this class does not implement. See SPEC-cli.md, 2.10.
 */
@Component
public class ArqSyncPipelineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArqSyncPipelineRunner.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final int EXIT_FATAL_ERROR = 1;

    private final ScannerService scannerService;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final PersistenceService persistenceService;
    private final ReportExporter reportExporter;
    private final ProcessExiter processExiter;

    public ArqSyncPipelineRunner(
            ScannerService scannerService,
            DependencyAnalyzer dependencyAnalyzer,
            PersistenceService persistenceService,
            ReportExporter reportExporter,
            ProcessExiter processExiter
    ) {
        this.scannerService = scannerService;
        this.dependencyAnalyzer = dependencyAnalyzer;
        this.persistenceService = persistenceService;
        this.reportExporter = reportExporter;
        this.processExiter = processExiter;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> nonOptionArgs = args.getNonOptionArgs();
        if (nonOptionArgs.isEmpty() || nonOptionArgs.get(0).isBlank()) {
            log.error("Uso: java -jar arqsync.jar <caminho-do-projeto>");
            processExiter.exit(EXIT_FATAL_ERROR);
            return;
        }

        Path path = Paths.get(nonOptionArgs.get(0));

        log.info("Scanning project...");
        ProjectScan projectScan;
        try {
            projectScan = scannerService.scan(path);
        } catch (InvalidProjectPathException e) {
            log.error(e.getMessage());
            processExiter.exit(EXIT_FATAL_ERROR);
            return;
        }

        log.info("Analyzing dependencies...");
        AnalysisResult analysisResult;
        try {
            analysisResult = dependencyAnalyzer.analyze(projectScan);
        } catch (Exception e) {
            log.error("Failed to analyze project: {}", e.getMessage(), e);
            processExiter.exit(EXIT_FATAL_ERROR);
            return;
        }

        log.info("Saving analysis...");
        // No try/catch: PersistenceService.save(...) never throws, by contract
        // (SPEC-persistence.md, 2.1; SPEC-cli.md, 2.8).
        persistenceService.save(projectScan, analysisResult);

        Path outputDir = Paths.get("arqsync-reports", LocalDateTime.now().format(TIMESTAMP_FORMAT));

        log.info("Generating report...");
        try {
            reportExporter.export(projectScan, analysisResult, outputDir);
        } catch (Exception e) {
            log.error("Failed to generate report: {}", e.getMessage(), e);
            processExiter.exit(EXIT_FATAL_ERROR);
            return;
        }

        reportFinalOutcome(outputDir);
    }

    private void reportFinalOutcome(Path outputDir) {
        Path htmlPath = outputDir.resolve("report.html");
        if (Files.exists(htmlPath)) {
            log.info("Report generated successfully at: {}", htmlPath);
        } else {
            log.info("Report generated at: {} (report.html was not generated - see warnings above)",
                    outputDir.resolve("report.json"));
        }
    }
}
