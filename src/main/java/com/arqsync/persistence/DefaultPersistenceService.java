package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA-backed {@link PersistenceService}. Registered as a bean only when
 * persistence is available at startup — see {@link PersistenceServiceConfiguration}
 * and {@link NoOpPersistenceService}. Not itself {@code @Service}-annotated:
 * component-scanning it directly would create it (and therefore require the
 * JPA repositories it depends on) unconditionally, defeating the point.
 */
public class DefaultPersistenceService implements PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultPersistenceService.class);

    private final PersistenceWriter persistenceWriter;

    public DefaultPersistenceService(PersistenceWriter persistenceWriter) {
        this.persistenceWriter = persistenceWriter;
    }

    /**
     * Never throws (SPEC-persistence.md, 2.1). {@link PersistenceWriter#persist}
     * is a separate bean, not a same-class method, precisely so that calling it
     * here goes through its real {@code @Transactional} proxy — see
     * {@link PersistenceWriter} for why that separation matters.
     */
    @Override
    public void save(ProjectScan projectScan, AnalysisResult analysisResult) {
        try {
            persistenceWriter.persist(projectScan, analysisResult);
        } catch (Exception e) {
            log.error("Failed to persist analysis for project at '{}': {}",
                    projectScan.rootPath(), e.getMessage(), e);
        }
    }
}
