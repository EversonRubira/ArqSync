package com.arqsync.bootstrap;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Probes database connectivity before the Spring context is created and, if
 * the database is unreachable, excludes the JPA/Flyway/DataSource
 * autoconfiguration entirely instead of letting it fail context startup.
 *
 * <p>Without this, an unreachable database blocks the whole application from
 * starting — Flyway's migration attempt at boot fails the context refresh
 * before {@code ArqSyncPipelineRunner} ever runs — which contradicts the
 * PRD's "resilient without a database" requirement at a level
 * {@code PersistenceService.save()}'s own fire-and-forget contract
 * (SPEC-persistence.md, 2.1) doesn't reach: that contract only covers
 * failures *during* a call, not the database being unreachable before the
 * application has even started.
 *
 * <p>Runs as an {@link EnvironmentPostProcessor} — the only extension point
 * available before any bean (including the DataSource itself) exists — and
 * is registered via {@code META-INF/spring.factories} (the {@code .imports}
 * file mechanism only covers {@code AutoConfiguration} classes as of Spring
 * Boot 3.4; {@code EnvironmentPostProcessor} is still spring.factories-based),
 * since it can't be discovered through component scanning at that point in
 * the startup sequence.
 */
public class PersistenceAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "arqsync-persistence-availability";
    private static final int CONNECTION_TIMEOUT_SECONDS = 3;

    private static final String[] AUTOCONFIGURATION_CLASSES_TO_EXCLUDE = {
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    };

    private final Log log;

    public PersistenceAvailabilityEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(PersistenceAvailabilityEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        if (url == null) {
            return;
        }

        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (isReachable(url, username, password)) {
            return;
        }

        log.warn("Database at '" + url + "' is unreachable - disabling persistence for this run "
                + "(the pipeline still runs; only saving to the database is skipped).");

        Map<String, Object> fallbackProperties = new LinkedHashMap<>();
        fallbackProperties.put("arqsync.persistence.enabled", "false");
        fallbackProperties.put("spring.autoconfigure.exclude", String.join(",", AUTOCONFIGURATION_CLASSES_TO_EXCLUDE));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, fallbackProperties));
    }

    private boolean isReachable(String url, String username, String password) {
        try {
            DriverManager.setLoginTimeout(CONNECTION_TIMEOUT_SECONDS);
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                return connection.isValid(CONNECTION_TIMEOUT_SECONDS);
            }
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
