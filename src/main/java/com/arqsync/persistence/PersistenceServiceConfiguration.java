package com.arqsync.persistence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Decides which {@link PersistenceService} bean is active. The actual
 * "is the database reachable" decision happens earlier, before the Spring
 * context is even created — see {@code PersistenceAvailabilityEnvironmentPostProcessor}
 * (com.arqsync.bootstrap), which sets {@code arqsync.persistence.enabled=false}
 * and excludes the JPA/Flyway/DataSource autoconfiguration entirely when the
 * database was found unreachable at startup. This class only wires the
 * matching {@link PersistenceService} implementation to that same decision.
 *
 * <p>{@link PersistenceWriter} is wired the same way (not component-scanned)
 * so that, when persistence is disabled, neither it nor {@link DefaultPersistenceService}
 * ever gets created — there would be no {@code ProjectRepository}/
 * {@code AnalysisRepository} beans for it to depend on in that case, since
 * the JPA autoconfiguration that provides them is excluded too.
 */
@Configuration
public class PersistenceServiceConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "arqsync.persistence", name = "enabled", havingValue = "true", matchIfMissing = true)
    PersistenceWriter persistenceWriter(ProjectRepository projectRepository, AnalysisRepository analysisRepository) {
        return new PersistenceWriter(projectRepository, analysisRepository);
    }

    @Bean
    @ConditionalOnBean(PersistenceWriter.class)
    public PersistenceService defaultPersistenceService(PersistenceWriter persistenceWriter) {
        return new DefaultPersistenceService(persistenceWriter);
    }

    @Bean
    @ConditionalOnMissingBean(PersistenceService.class)
    public PersistenceService noOpPersistenceService() {
        return new NoOpPersistenceService();
    }
}
