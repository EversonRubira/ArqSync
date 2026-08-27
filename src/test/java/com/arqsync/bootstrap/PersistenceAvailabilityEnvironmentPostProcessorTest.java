package com.arqsync.bootstrap;

import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceAvailabilityEnvironmentPostProcessorTest {

    private final PersistenceAvailabilityEnvironmentPostProcessor postProcessor =
            new PersistenceAvailabilityEnvironmentPostProcessor(
                    source -> LogFactory.getLog(PersistenceAvailabilityEnvironmentPostProcessor.class)
            );

    @Test
    void disablesPersistenceAndExcludesAutoconfigurationWhenDatabaseIsUnreachable() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:postgresql://localhost:1/no-such-db");
        environment.setProperty("spring.datasource.username", "nobody");
        environment.setProperty("spring.datasource.password", "nothing");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("arqsync.persistence.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.autoconfigure.exclude"))
                .contains("HibernateJpaAutoConfiguration")
                .contains("JpaRepositoriesAutoConfiguration")
                .contains("DataSourceAutoConfiguration")
                .contains("DataSourceTransactionManagerAutoConfiguration")
                .contains("FlywayAutoConfiguration");
    }

    @Test
    void leavesPersistenceEnabledWhenDatabaseIsReachable() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.url", "jdbc:h2:mem:persistence-availability-test;DB_CLOSE_DELAY=-1");
        environment.setProperty("spring.datasource.username", "sa");
        environment.setProperty("spring.datasource.password", "");

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("arqsync.persistence.enabled")).isNull();
        assertThat(environment.getProperty("spring.autoconfigure.exclude")).isNull();
    }

    @Test
    void doesNothingWhenNoDatasourceUrlIsConfigured() {
        MockEnvironment environment = new MockEnvironment();

        postProcessor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("arqsync.persistence.enabled")).isNull();
    }
}
