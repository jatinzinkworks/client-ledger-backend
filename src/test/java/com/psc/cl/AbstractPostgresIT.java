package com.psc.cl;

import com.psc.cl.config.TimeZones;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that run the whole application against a real PostgreSQL instance.
 *
 * <p>Unlike the sliced tests, these start the full context: Flyway applies every migration, and
 * Hibernate then validates each entity mapping against the schema it produced. That is the only
 * place the migrations, the mappings and the Spring Data derived queries are actually exercised.
 *
 * <p>Tagged {@code integration} so a run without Docker available can skip them with
 * {@code -DexcludedGroups=integration}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
public abstract class AbstractPostgresIT {

    /**
     * One container for the whole suite. It is started from a static initialiser rather than with
     * {@code @Testcontainers} so every subclass shares the single instance instead of paying for a
     * fresh database per class; Ryuk stops it when the JVM exits.
     *
     * <p>The image matches docker-compose.yaml so the tests run against the same major version as
     * local development.
     */
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("client_ledger_test")
            .withUsername("postgres")
            .withPassword("password");

    static {
        // Before the container, and so before any connection: the PostgreSQL driver sends the
        // default zone of the JVM on connect, and a host zone the server rejects fails Flyway at
        // context load. The static initialiser on ClientLedgerApplication covers production, but
        // it never runs here because tests do not call main.
        TimeZones.pinToUtc();
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
