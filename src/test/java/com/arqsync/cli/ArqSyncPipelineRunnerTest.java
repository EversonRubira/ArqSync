package com.arqsync.cli;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.analyzer.DefaultCycleDetector;
import com.arqsync.analyzer.DefaultDependencyAnalyzer;
import com.arqsync.analyzer.DefaultDependencyGraphBuilder;
import com.arqsync.analyzer.DefaultLayerViolationDetector;
import com.arqsync.analyzer.DefaultMetricsCalculator;
import com.arqsync.analyzer.DependencyAnalyzer;
import com.arqsync.exporter.ReportExporter;
import com.arqsync.persistence.PersistenceService;
import com.arqsync.scanner.DefaultJavaParserAdapter;
import com.arqsync.scanner.DefaultScannerService;
import com.arqsync.scanner.InvalidProjectPathException;
import com.arqsync.scanner.ProjectScan;
import com.arqsync.scanner.ScannerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ArqSyncPipelineRunnerTest {

    private final ScannerService scannerService = mock(ScannerService.class);
    private final DependencyAnalyzer dependencyAnalyzer = mock(DependencyAnalyzer.class);
    private final PersistenceService persistenceService = mock(PersistenceService.class);
    private final ReportExporter reportExporter = mock(ReportExporter.class);
    private final ProcessExiter processExiter = mock(ProcessExiter.class);

    private final GitRepositoryResolver gitRepositoryResolver = new GitRepositoryResolver();

    private final ArqSyncPipelineRunner runner = new ArqSyncPipelineRunner(
            gitRepositoryResolver, scannerService, dependencyAnalyzer, persistenceService, reportExporter, processExiter
    );

    private Path createdOutputDir;

    @AfterEach
    void cleanUpAnyGeneratedReportDir() throws IOException {
        if (createdOutputDir != null && Files.exists(createdOutputDir)) {
            try (var walk = Files.walk(createdOutputDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // best-effort cleanup
                    }
                });
            }
            Path reportsRoot = Paths.get("arqsync-reports");
            if (Files.exists(reportsRoot) && isEmptyDirectory(reportsRoot)) {
                Files.deleteIfExists(reportsRoot);
            }
        }
    }

    private boolean isEmptyDirectory(Path dir) throws IOException {
        try (var entries = Files.list(dir)) {
            return entries.findAny().isEmpty();
        }
    }

    private ApplicationArguments argsWith(String... nonOptionArgs) {
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getNonOptionArgs()).thenReturn(List.of(nonOptionArgs));
        return args;
    }

    private ProjectScan aProjectScan() {
        return new ProjectScan("/repo/my-project", List.of(), List.of());
    }

    private AnalysisResult anAnalysisResult() {
        return new AnalysisResult(
                new com.arqsync.analyzer.DependencyGraph(java.util.Set.of(), List.of()),
                List.of(), List.of(),
                new com.arqsync.analyzer.AnalysisMetrics(0, 0, 0, 0, List.of())
        );
    }

    @Test
    void validPathRunsTheFullPipelineInOrderAndNeverExits() {
        ProjectScan projectScan = aProjectScan();
        AnalysisResult analysisResult = anAnalysisResult();
        when(scannerService.scan(any())).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenReturn(analysisResult);
        doAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            createdOutputDir = outputDir;
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("report.json"), "{}");
            return null;
        }).when(reportExporter).export(eq(projectScan), eq(analysisResult), any());

        runner.run(argsWith("/repo/my-project"));

        verify(scannerService).scan(Paths.get("/repo/my-project"));
        verify(dependencyAnalyzer).analyze(projectScan);
        verify(persistenceService).save(projectScan, analysisResult);
        verify(reportExporter).export(eq(projectScan), eq(analysisResult), any());
        verify(processExiter, never()).exit(anyInt());
        assertThat(createdOutputDir.resolve("report.json")).exists();
    }

    @Test
    void invalidPathStopsBeforeAnalyzerPersistenceOrExporter() {
        when(scannerService.scan(any())).thenThrow(new InvalidProjectPathException("bad path"));

        runner.run(argsWith("/does/not/exist"));

        verify(processExiter).exit(1);
        verifyNoInteractions(dependencyAnalyzer, persistenceService, reportExporter);
    }

    @Test
    void analyzerExceptionStopsBeforePersistenceOrExporter() {
        ProjectScan projectScan = aProjectScan();
        when(scannerService.scan(any())).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenThrow(new RuntimeException("boom"));

        runner.run(argsWith("/repo/my-project"));

        verify(processExiter).exit(1);
        verifyNoInteractions(persistenceService, reportExporter);
    }

    @Test
    void persistenceIsCalledAndPipelineContinuesToExporterRegardlessOfWhatItDoes() {
        ProjectScan projectScan = aProjectScan();
        AnalysisResult analysisResult = anAnalysisResult();
        when(scannerService.scan(any())).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenReturn(analysisResult);
        doAnswer(invocation -> {
            createdOutputDir = invocation.getArgument(2);
            return null;
        }).when(reportExporter).export(any(), any(), any());

        runner.run(argsWith("/repo/my-project"));

        verify(persistenceService).save(projectScan, analysisResult);
        verify(reportExporter).export(eq(projectScan), eq(analysisResult), any());
        verify(processExiter, never()).exit(anyInt());
    }

    @Test
    void missingHtmlIsNotFatalAndDoesNotCallExit() throws IOException {
        ProjectScan projectScan = aProjectScan();
        AnalysisResult analysisResult = anAnalysisResult();
        when(scannerService.scan(any())).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenReturn(analysisResult);
        doAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            createdOutputDir = outputDir;
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("report.json"), "{}");
            // no report.html written - simulates Python being unavailable
            return null;
        }).when(reportExporter).export(any(), any(), any());

        runner.run(argsWith("/repo/my-project"));

        assertThat(createdOutputDir.resolve("report.json")).exists();
        assertThat(createdOutputDir.resolve("report.html")).doesNotExist();
        verify(processExiter, never()).exit(anyInt());
    }

    @Test
    void reportExporterExceptionIsFatal() {
        ProjectScan projectScan = aProjectScan();
        AnalysisResult analysisResult = anAnalysisResult();
        when(scannerService.scan(any())).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenReturn(analysisResult);
        doAnswer(invocation -> {
            throw new RuntimeException("could not write report.json");
        }).when(reportExporter).export(any(), any(), any());

        runner.run(argsWith("/repo/my-project"));

        verify(processExiter).exit(1);
    }

    @Test
    void missingArgumentExitsFatalWithoutCallingAnyComponent() {
        runner.run(argsWith());

        verify(processExiter).exit(1);
        verifyNoInteractions(scannerService, dependencyAnalyzer, persistenceService, reportExporter);
    }

    @Test
    void blankArgumentExitsFatalWithoutCallingAnyComponent() {
        runner.run(argsWith("   "));

        verify(processExiter).exit(1);
        verifyNoInteractions(scannerService, dependencyAnalyzer, persistenceService, reportExporter);
    }

    @Test
    void fullPipelineWithRealScannerAndAnalyzerAgainstAFixture() throws URISyntaxException {
        ScannerService realScanner = new DefaultScannerService(new DefaultJavaParserAdapter());
        DependencyAnalyzer realAnalyzer = new DefaultDependencyAnalyzer(
                new DefaultDependencyGraphBuilder(),
                new DefaultCycleDetector(),
                new DefaultLayerViolationDetector(),
                new DefaultMetricsCalculator()
        );
        ArqSyncPipelineRunner realishRunner = new ArqSyncPipelineRunner(
                gitRepositoryResolver, realScanner, realAnalyzer, persistenceService, reportExporter, processExiter
        );
        Path fixture = Paths.get(Objects.requireNonNull(
                getClass().getClassLoader().getResource("fixtures/scanner/valid-project")
        ).toURI());

        doAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            createdOutputDir = outputDir;
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("report.json"), "{}");
            Files.writeString(outputDir.resolve("report.html"), "<html></html>");
            return null;
        }).when(reportExporter).export(any(), any(), any());

        realishRunner.run(argsWith(fixture.toString()));

        verify(persistenceService).save(any(), any());
        verify(reportExporter).export(any(), any(), any());
        verify(processExiter, never()).exit(anyInt());
        assertThat(createdOutputDir.resolve("report.html")).exists();
    }

    private static final String GIT_URL = "https://example.com/user/repo.git";

    private ApplicationArguments argsWith(String nonOptionArg, boolean keep) {
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.getNonOptionArgs()).thenReturn(List.of(nonOptionArg));
        when(args.containsOption("keep")).thenReturn(keep);
        return args;
    }

    private ArqSyncPipelineRunner runnerWithMockedGitResolver(GitRepositoryResolver mockResolver) {
        return new ArqSyncPipelineRunner(
                mockResolver, scannerService, dependencyAnalyzer, persistenceService, reportExporter, processExiter
        );
    }

    @Test
    void gitUrlIsClonedScannedAndTempDirCleanedUpOnSuccess(@TempDir Path clonedDir) throws IOException {
        GitRepositoryResolver mockResolver = mock(GitRepositoryResolver.class);
        when(mockResolver.resolve(GIT_URL)).thenReturn(clonedDir);
        ArqSyncPipelineRunner urlRunner = runnerWithMockedGitResolver(mockResolver);

        ProjectScan projectScan = aProjectScan();
        AnalysisResult analysisResult = anAnalysisResult();
        when(scannerService.scan(clonedDir)).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenReturn(analysisResult);
        doAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            createdOutputDir = outputDir;
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("report.json"), "{}");
            return null;
        }).when(reportExporter).export(eq(projectScan), eq(analysisResult), any());

        urlRunner.run(argsWith(GIT_URL, false));

        verify(mockResolver).resolve(GIT_URL);
        verify(scannerService).scan(clonedDir);
        verify(processExiter, never()).exit(anyInt());
        assertThat(clonedDir).doesNotExist();
    }

    @Test
    void gitUrlTempDirIsCleanedUpEvenWhenAnalyzerFails(@TempDir Path clonedDir) {
        GitRepositoryResolver mockResolver = mock(GitRepositoryResolver.class);
        when(mockResolver.resolve(anyString())).thenReturn(clonedDir);
        ArqSyncPipelineRunner urlRunner = runnerWithMockedGitResolver(mockResolver);

        ProjectScan projectScan = aProjectScan();
        when(scannerService.scan(clonedDir)).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenThrow(new RuntimeException("boom"));

        urlRunner.run(argsWith(GIT_URL, false));

        verify(processExiter).exit(1);
        assertThat(clonedDir).doesNotExist();
    }

    @Test
    void keepFlagPreventsTempDirCleanupAfterSuccess(@TempDir Path clonedDir) throws IOException {
        GitRepositoryResolver mockResolver = mock(GitRepositoryResolver.class);
        when(mockResolver.resolve(GIT_URL)).thenReturn(clonedDir);
        ArqSyncPipelineRunner urlRunner = runnerWithMockedGitResolver(mockResolver);

        ProjectScan projectScan = aProjectScan();
        AnalysisResult analysisResult = anAnalysisResult();
        when(scannerService.scan(clonedDir)).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenReturn(analysisResult);
        doAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            createdOutputDir = outputDir;
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve("report.json"), "{}");
            return null;
        }).when(reportExporter).export(eq(projectScan), eq(analysisResult), any());

        urlRunner.run(argsWith(GIT_URL, true));

        verify(processExiter, never()).exit(anyInt());
        assertThat(clonedDir).exists();
    }

    @Test
    void keepFlagAlsoPreventsTempDirCleanupOnFailure(@TempDir Path clonedDir) {
        GitRepositoryResolver mockResolver = mock(GitRepositoryResolver.class);
        when(mockResolver.resolve(anyString())).thenReturn(clonedDir);
        ArqSyncPipelineRunner urlRunner = runnerWithMockedGitResolver(mockResolver);

        ProjectScan projectScan = aProjectScan();
        when(scannerService.scan(clonedDir)).thenReturn(projectScan);
        when(dependencyAnalyzer.analyze(projectScan)).thenThrow(new RuntimeException("boom"));

        urlRunner.run(argsWith(GIT_URL, true));

        verify(processExiter).exit(1);
        assertThat(clonedDir).exists();
    }

    @Test
    void gitCloneFailureExitsFatalWithoutCallingScannerOrLaterStages() {
        GitRepositoryResolver mockResolver = mock(GitRepositoryResolver.class);
        when(mockResolver.resolve(anyString()))
                .thenThrow(new GitCloneException("Invalid repository URL: " + GIT_URL));
        ArqSyncPipelineRunner urlRunner = runnerWithMockedGitResolver(mockResolver);

        urlRunner.run(argsWith(GIT_URL, false));

        verify(processExiter).exit(1);
        verifyNoInteractions(scannerService, dependencyAnalyzer, persistenceService, reportExporter);
    }
}
