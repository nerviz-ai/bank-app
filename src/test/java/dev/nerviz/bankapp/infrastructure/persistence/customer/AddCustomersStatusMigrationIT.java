package dev.nerviz.bankapp.infrastructure.persistence.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * {@code V3} behavior only shows against the real engine with data already in the table: the
 * schema is migrated to {@code V2} on its own PostgreSQL schema, one row is inserted, then
 * {@code V3} runs. The shared {@code public} schema is never touched.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIf("dockerAvailable")
class AddCustomersStatusMigrationIT {

    private static final String SCHEMA = "migration_v3_it";

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void dropSchema() {
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    void backfillsExistingRowsAsActive() {
        migrateTo("2");
        jdbcTemplate.update(
                "INSERT INTO " + SCHEMA + ".customers (id, name, security_number, birth_date, registered_at)"
                        + " VALUES (gen_random_uuid(), 'Maria Silva', '12345678901', DATE '1990-05-17', now())");

        migrateTo("3");

        assertThat(jdbcTemplate.queryForList("SELECT status FROM " + SCHEMA + ".customers", String.class))
                .containsExactly("ACTIVE");
    }

    @Test
    void leavesNoDefaultOnStatus() {
        migrateTo("3");

        String columnDefault = jdbcTemplate.queryForObject(
                "SELECT column_default FROM information_schema.columns"
                        + " WHERE table_schema = ? AND table_name = 'customers' AND column_name = 'status'",
                String.class,
                SCHEMA);

        assertThat(columnDefault).isNull();
    }

    private void migrateTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }
}
