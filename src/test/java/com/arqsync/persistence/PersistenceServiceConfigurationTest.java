package com.arqsync.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the conditional wiring in isolation, without needing a full
 * database (real or H2) — {@link ProjectRepository}/{@link AnalysisRepository}
 * are supplied as mocks, since the point here is which {@link PersistenceService}
 * implementation gets activated by {@code arqsync.persistence.enabled}, not the
 * repositories' own behavior.
 */
class PersistenceServiceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PersistenceServiceConfiguration.class)
            .withBean(ProjectRepository.class, () -> mock(ProjectRepository.class))
            .withBean(AnalysisRepository.class, () -> mock(AnalysisRepository.class));

    @Test
    void wiresDefaultPersistenceServiceByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PersistenceService.class);
            assertThat(context.getBean(PersistenceService.class)).isInstanceOf(DefaultPersistenceService.class);
            assertThat(context).hasSingleBean(PersistenceWriter.class);
        });
    }

    @Test
    void wiresDefaultPersistenceServiceWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues("arqsync.persistence.enabled=true").run(context ->
                assertThat(context.getBean(PersistenceService.class)).isInstanceOf(DefaultPersistenceService.class)
        );
    }

    @Test
    void wiresNoOpPersistenceServiceWhenDisabled() {
        contextRunner.withPropertyValues("arqsync.persistence.enabled=false").run(context -> {
            assertThat(context).hasSingleBean(PersistenceService.class);
            assertThat(context.getBean(PersistenceService.class)).isInstanceOf(NoOpPersistenceService.class);
            assertThat(context).doesNotHaveBean(PersistenceWriter.class);
        });
    }
}
