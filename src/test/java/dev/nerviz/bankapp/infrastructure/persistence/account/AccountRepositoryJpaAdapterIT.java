package dev.nerviz.bankapp.infrastructure.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.AccountFixtures;
import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.model.Account;
import dev.nerviz.bankapp.domain.model.Customer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * Against the real engine, at the production version: mapping round trip and the constraints
 * {@code V6} declares. The owner customer is inserted through the {@code CustomerRepository}
 * port, never through the other aggregate's entity.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, AccountRepositoryJpaAdapter.class})
@EnabledIf("dockerAvailable")
class AccountRepositoryJpaAdapterIT {

    private static final Instant OPENED_AT = Instant.parse("2026-01-15T10:00:00Z");

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @TestConfiguration
    @ComponentScan(
            basePackages = "dev.nerviz.bankapp.infrastructure.persistence.customer",
            useDefaultFilters = false,
            includeFilters =
                    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CustomerRepository.class))
    static class FixedClockAndCustomerPort {

        @Bean
        Clock clock() {
            return Clock.fixed(OPENED_AT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private AccountRepositoryJpaAdapter adapter;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEntityManager entityManager;

    private Customer savedCustomer() {
        return customerRepository.save(CustomerFixtures.activeCustomer());
    }

    @Test
    void savesAndReadsBack() {
        Customer owner = savedCustomer();
        Account account = AccountFixtures.openFor(owner);

        Account saved = adapter.save(account);
        entityManager.clear();

        assertThat(saved).isEqualTo(account);
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT id, customer_id, balance, status, version FROM accounts WHERE id = ?",
                        account.id().value()))
                .containsEntry("id", account.id().value())
                .containsEntry("customer_id", owner.id().value())
                .containsEntry("balance", new BigDecimal("0.00"))
                .containsEntry("status", "ACTIVE")
                .containsEntry("version", 0L);
        OffsetDateTime openedAt = jdbcTemplate.queryForObject(
                "SELECT opened_at FROM accounts WHERE id = ?",
                OffsetDateTime.class,
                account.id().value());
        assertThat(openedAt).isNotNull();
        assertThat(openedAt.toInstant()).isEqualTo(OPENED_AT);
    }

    @Test
    void holdsManyAccountsPerCustomer() {
        Customer owner = savedCustomer();

        adapter.save(AccountFixtures.openFor(owner));
        adapter.save(AccountFixtures.openFor(owner));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE customer_id = ?",
                Integer.class,
                owner.id().value());
        assertThat(count).isEqualTo(2);
    }

    /** Proves the adapter flushes inside its own try/catch: {@code saveAndFlush}, not {@code save}. */
    @Test
    void rejectsUnknownCustomerByForeignKey() {
        Account orphan = AccountFixtures.openFor(CustomerFixtures.activeCustomer());

        assertThatThrownBy(() -> adapter.save(orphan))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("ACCOUNT_PERSISTENCE_CONFLICT");
    }

    @Test
    void rejectsDuplicateAccountId() {
        Customer owner = savedCustomer();
        Account account = AccountFixtures.openFor(owner);
        adapter.save(account);
        entityManager.clear();

        assertThatThrownBy(() -> adapter.save(account))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("ACCOUNT_PERSISTENCE_CONFLICT");
    }

    @Test
    void rejectsNegativeBalanceByCheck() {
        UUID ownerId = savedCustomer().id().value();
        UUID accountId = UUID.randomUUID();
        String insert = "INSERT INTO accounts (id, customer_id, balance, status, opened_at, version)"
                + " VALUES (?, ?, -0.01, 'ACTIVE', now(), 0)";

        assertThatThrownBy(() -> jdbcTemplate.update(insert, accountId, ownerId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_balance_non_negative");
    }

    @Test
    void rejectsUnknownStatusByCheck() {
        UUID ownerId = savedCustomer().id().value();
        UUID accountId = UUID.randomUUID();
        String insert = "INSERT INTO accounts (id, customer_id, balance, status, opened_at, version)"
                + " VALUES (?, ?, 0, 'CLOSED', now(), 0)";

        assertThatThrownBy(() -> jdbcTemplate.update(insert, accountId, ownerId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_status");
    }

    @Test
    void indexesAccountsByCustomer() {
        Integer indexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'accounts' AND indexname = 'ix_accounts_customer_id'",
                Integer.class);

        assertThat(indexes).isEqualTo(1);
    }
}
