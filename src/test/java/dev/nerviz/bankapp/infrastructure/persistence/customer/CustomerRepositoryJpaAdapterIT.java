package dev.nerviz.bankapp.infrastructure.persistence.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * Against the real engine, at the production version. What this level verifies: mapping,
 * {@code char(11)}, re-read of the aggregate, unique-constraint translation. Business
 * rules have their test in the domain.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, CustomerRepositoryJpaAdapter.class})
@EnabledIf("dockerAvailable")
class CustomerRepositoryJpaAdapterIT {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private CustomerRepositoryJpaAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void persistsAndRereadsTheSameAggregate() {
        Customer customer = newCustomer("12345678901", LocalDate.of(1990, 5, 17));

        Customer saved = adapter.save(customer);

        assertThat(adapter.existsBySecurityNumber(saved.securityNumber())).isTrue();
        assertThat(saved.name()).isEqualTo("Maria Silva");
        assertThat(saved.securityNumber().value()).isEqualTo("12345678901");
        assertThat(saved.birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(saved.registeredAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void reportsWhetherSecurityNumberExists() {
        SecurityNumber unregistered = SecurityNumber.of("99999999999");

        assertThat(adapter.existsBySecurityNumber(unregistered)).isFalse();

        adapter.save(newCustomer("98765432109", LocalDate.of(1990, 5, 17)));

        assertThat(adapter.existsBySecurityNumber(SecurityNumber.of("98765432109")))
                .isTrue();
    }

    /** Proves the adapter flushes inside its own try/catch — {@code saveAndFlush}, not {@code save}. */
    @Test
    void rejectsDuplicateSecurityNumber() {
        adapter.save(newCustomer("11122233344", LocalDate.of(1990, 5, 17)));
        Customer duplicate = newCustomer("11122233344", LocalDate.of(1991, 6, 20));

        assertThatThrownBy(() -> adapter.save(duplicate))
                .isInstanceOf(SecurityNumberAlreadyRegisteredException.class)
                .extracting("errorCode")
                .isEqualTo("SECURITY_NUMBER_ALREADY_REGISTERED");
    }

    @Test
    void findsSavedCustomerById() {
        Customer saved = adapter.save(newCustomer("22233344455", LocalDate.of(1988, 3, 9)));

        Optional<Customer> found = adapter.findById(saved.id());

        assertThat(found).hasValue(saved);
    }

    @ParameterizedTest
    @EnumSource(CustomerStatus.class)
    void savesAndReadsStatus(CustomerStatus status) {
        Customer customer = Customer.rehydrate(
                CustomerId.of(UUID.randomUUID()),
                "Maria Silva",
                SecurityNumber.of("33344455566"),
                LocalDate.of(1990, 5, 17),
                CLOCK.instant(),
                status);

        adapter.save(customer);
        entityManager.clear();

        assertThat(adapter.findById(customer.id()))
                .hasValueSatisfying(found -> assertThat(found.status()).isEqualTo(status));
    }

    @Test
    void rejectsUnknownStatusAtDatabase() {
        UUID id = UUID.randomUUID();
        String insert = "INSERT INTO customers (id, name, security_number, birth_date, registered_at, status, version)"
                + " VALUES (?, 'Maria Silva', '44455566677', DATE '1990-05-17', now(), 'UNKNOWN', 0)";

        assertThatThrownBy(() -> jdbcTemplate.update(insert, id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_customers_status");
    }

    @Test
    void returnsEmptyForUnknownId() {
        CustomerId unknown = CustomerId.of(UUID.fromString("00000000-0000-7000-8000-000000000000"));

        Optional<Customer> found = adapter.findById(unknown);

        assertThat(found).isEmpty();
    }

    private static Customer newCustomer(String securityNumber, LocalDate birthDate) {
        return Customer.register(
                CustomerId.of(UUID.randomUUID()), "Maria Silva", SecurityNumber.of(securityNumber), birthDate, CLOCK);
    }
}
