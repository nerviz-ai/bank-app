package dev.nerviz.bankapp.infrastructure.persistence.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
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

    private static Customer newCustomer(String securityNumber, LocalDate birthDate) {
        return Customer.register(
                CustomerId.of(UUID.randomUUID()), "Maria Silva", SecurityNumber.of(securityNumber), birthDate, CLOCK);
    }
}
