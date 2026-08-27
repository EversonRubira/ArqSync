package com.arqsync.analyzer;

import com.arqsync.scanner.ProjectScan;

/**
 * Entry point of the Analyzer component. Pure, deterministic, no I/O.
 */
public interface DependencyAnalyzer {

    AnalysisResult analyze(ProjectScan projectScan);
}
