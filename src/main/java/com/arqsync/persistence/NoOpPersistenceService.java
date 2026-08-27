package com.arqsync.persistence;

import com.arqsync.analyzer.AnalysisResult;
import com.arqsync.scanner.ProjectScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Active when the database was found unreachable at startup (see
 * {@code PersistenceAvailabilityEnvironmentPostProcessor} and
 * {@link PersistenceServiceConfiguration}) — the JPA/Flyway/DataSource
 * autoconfiguration is excluded from the context in that case, so no
 * repository beans exist for a JPA-backed implementation to depend on.
 *
 * <p>Fulfils the same fire-and-forget contract as {@link DefaultPersistenceService}
 * (never throws), just without anywhere to actually save to.
 */
public class NoOpPersistenceService implements PersistenceService {

    private static final Logger log = LoggerFactory.getLogger(NoOpPersistenceService.class);

    @Override
    public void save(ProjectScan projectScan, AnalysisResult analysisResult) {
        log.warn("Persistence is disabled (database was unreachable at startup) - "
                + "skipping save for project at '{}'.", projectScan.rootPath());
    }
}
