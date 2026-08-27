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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates Scanner -> Analyzer -> Persistence -> Exporter (SPEC-cli.md).
 *
 * <p>Named {@code ArqSyncPipelineRunner}, not {@code CommandLineRunner} as the
 * Spec's interface section literally shows — that name collides with
 * {@link org.springframework.boot.CommandLineRunner}, an unrelated Spring Boot
 * interface this class does not implement. See SPEC-cli.md, 2.10.
 *
 * <p>The single positional argument accepts either a local project path or a
 * Git repository URL (SPEC-cli.md, "Entrada via URL"): a URL is cloned into a
 * temporary directory by {@link GitRepositoryResolver}, analyzed like any
 * local project, then deleted — unless {@code --keep} is passed.
 */
@Component
public class ArqSyncPipelineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArqSyncPipelineRunner.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final int EXIT_FATAL_ERROR = 1;
    private static final long TOTAL_ANALYSIS_TIMEOUT_MINUTES = 10;

    private final GitRepositoryResolver gitRepositoryResolver;
    private final ScannerService scannerService;
    private final DependencyAnalyzer dependencyAnalyzer;
    private final PersistenceService persistenceService;
    private final ReportExporter reportExporter;
    private final ProcessExiter processExiter;

    public ArqSyncPipelineRunner(
            GitRepositoryResolver gitRepositoryResolver,
            ScannerService scannerService,
            DependencyAnalyzer dependencyAnalyzer,
            PersistenceService persistenceService,
            ReportExporter reportExporter,
            ProcessExiter processExiter
    ) {
        this.gitRepositoryResolver = gitRepositoryResolver;
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
            log.error("Uso: java -jar arqsync.jar <caminho-do-projeto | URL-do-repositorio> [--keep]");
            processExiter.exit(EXIT_FATAL_ERROR);
            return;
        }

        String argument = nonOptionArgs.get(0);
        boolean keep = args.containsOption("keep");

        ProjectScan projectScan;
        Path clonedDir = null;
        if (GitRepositoryResolver.isGitUrl(argument)) {
            try {
                CloneAndScanResult result = cloneAndScanWithinTimeout(argument);
                clonedDir = result.clonedDir();
                projectScan = result.projectScan();
            } catch (GitCloneOrScanFailure failure) {
                log.error(failure.getMessage());
                cleanupTempCloneDir(failure.clonedDir(), keep);
                processExiter.exit(EXIT_FATAL_ERROR);
                return;
            }
        } else {
            log.info("Scanning project...");
            try {
                projectScan = scannerService.scan(Paths.get(argument));
            } catch (InvalidProjectPathException e) {
                log.error(e.getMessage());
                processExiter.exit(EXIT_FATAL_ERROR);
                return;
            }
        }

        try {
            runAnalysisPersistenceAndExport(projectScan);
        } finally {
            cleanupTempCloneDir(clonedDir, keep);
        }
    }

    private void runAnalysisPersistenceAndExport(ProjectScan projectScan) {
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

    /**
     * Clones the given URL and scans the result as a single unit of work,
     * bounded by {@link #TOTAL_ANALYSIS_TIMEOUT_MINUTES} total (clone +
     * scan), per SPEC-cli.md ("Entrada via URL", limitações). Runs on a
     * dedicated thread so the timeout can be enforced even if the clone or
     * scan never returns on its own.
     */
    private CloneAndScanResult cloneAndScanWithinTimeout(String url) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Path> clonedDirRef = new AtomicReference<>();
        try {
            Future<ProjectScan> future = executor.submit(() -> {
                Path cloned = gitRepositoryResolver.resolve(url);
                clonedDirRef.set(cloned);
                log.info("Analyzing cloned repository...");
                return scannerService.scan(cloned);
            });
            ProjectScan projectScan = future.get(TOTAL_ANALYSIS_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            return new CloneAndScanResult(clonedDirRef.get(), projectScan);
        } catch (TimeoutException e) {
            throw new GitCloneOrScanFailure(
                    "Analysis timed out after " + TOTAL_ANALYSIS_TIMEOUT_MINUTES + " minutes", clonedDirRef.get());
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new GitCloneOrScanFailure(cause.getMessage(), clonedDirRef.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitCloneOrScanFailure("Analysis interrupted", clonedDirRef.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private void cleanupTempCloneDir(Path clonedDir, boolean keep) {
        if (clonedDir == null) {
            return;
        }
        if (keep) {
            log.warn("Temporary directory kept at {}", clonedDir);
            return;
        }
        log.info("Cleaning up temporary directory...");
        GitRepositoryResolver.deleteRecursively(clonedDir);
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

    private record CloneAndScanResult(Path clonedDir, ProjectScan projectScan) {
    }

    private static final class GitCloneOrScanFailure extends RuntimeException {
        private final Path clonedDir;

        GitCloneOrScanFailure(String message, Path clonedDir) {
            super(message);
            this.clonedDir = clonedDir;
        }

        Path clonedDir() {
            return clonedDir;
        }
    }
}
