package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;

/**
 * Saves one scan's results. Fire-and-forget: never throws, regardless of
 * whether the database is reachable (SPEC-persistence.md, 2.1).
 */
public interface PersistenceService {

    void save(ProjectScan projectScan, AnalysisResult analysisResult);
}
