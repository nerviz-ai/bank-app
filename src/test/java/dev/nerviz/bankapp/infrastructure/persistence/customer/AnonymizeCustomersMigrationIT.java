package dev.nerviz.bankapp.infrastructure.persistence.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.DockerClientFactory;

/**
 * {@code V5} only shows against the real engine with data already in the table: the schema is
 * migrated to {@code V4} on its own PostgreSQL schema, one row is inserted, then {@code V5} runs.
 * The shared {@code public} schema is never touched.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIf("dockerAvailable")
class AnonymizeCustomersMigrationIT {

    private static final String SCHEMA = "migration_v5_it";
    private static final String INSERT = "INSERT INTO " + SCHEMA
            + ".customers (id, name, security_number, birth_date, registered_at, status, anonymized_at, version)"
            + " VALUES (gen_random_uuid(), ?, ?, ?::date, now(), 'ACTIVE', ?::timestamptz, 0)";

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
    void existingRowsSatisfyIdentityCheck() {
        migrateTo("4");
        jdbcTemplate.update(
                "INSERT INTO " + SCHEMA + ".customers (id, name, security_number, birth_date, registered_at, status)"
                        + " VALUES (gen_random_uuid(), 'Maria Silva', '12345678901', DATE '1990-05-17', now(),"
                        + " 'ACTIVE')");

        migrateTo("5");

        assertThat(jdbcTemplate.queryForList(
                        "SELECT security_number FROM " + SCHEMA + ".customers WHERE anonymized_at IS NULL",
                        String.class))
                .containsExactly("12345678901");
    }

    @Test
    void acceptsFullyAnonymizedRow() {
        migrateTo("5");

        jdbcTemplate.update(INSERT, "ANONYMIZED", null, null, "2026-01-15T10:00:00Z");

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + SCHEMA + ".customers", Integer.class))
                .isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("halfAnonymizedRows")
    void rejectsHalfAnonymizedRow(String name, String securityNumber, String birthDate, String anonymizedAt) {
        migrateTo("5");

        assertThatThrownBy(() -> jdbcTemplate.update(INSERT, name, securityNumber, birthDate, anonymizedAt))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_customers_identity");
    }

    private static Stream<Arguments> halfAnonymizedRows() {
        return Stream.of(
                Arguments.of("ANONYMIZED", "12345678901", null, "2026-01-15T10:00:00Z"),
                Arguments.of("Maria Silva", null, null, null),
                Arguments.of("Maria Silva", null, null, "2026-01-15T10:00:00Z"));
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
