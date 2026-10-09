package dev.nerviz.bankapp;

import dev.nerviz.bankapp.application.usecase.customer.CreateCustomerCommand;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** Returns the valid case; each test changes only the field under test. */
public final class CustomerFixtures {

    public static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

    public static final String NAME = "Maria Silva";
    public static final String SECURITY_NUMBER = "12345678901";
    public static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 17);

    private CustomerFixtures() {}

    public static Customer customer() {
        return Customer.register(
                CustomerId.of(UUID.randomUUID()), NAME, SecurityNumber.of(SECURITY_NUMBER), BIRTH_DATE, FIXED_CLOCK);
    }

    /**
     * Read-side tests only (persistence, controller): the state is rebuilt through
     * {@code rehydrate}, never reached through a use case, because no production path moves a
     * customer out of {@code KYC_IN_PROGRESS} yet.
     */
    public static Customer withStatus(CustomerStatus status) {
        Customer base = customer();
        return Customer.rehydrate(
                base.id(), base.name(), base.securityNumber(), base.birthDate(), base.registeredAt(), status);
    }

    public static CreateCustomerCommand command() {
        return new CreateCustomerCommand(NAME, SECURITY_NUMBER, BIRTH_DATE);
    }

    public static String requestJson() {
        return """
                {
                  "name": "%s",
                  "securityNumber": "%s",
                  "birthDate": "%s"
                }
                """.formatted(NAME, SECURITY_NUMBER, BIRTH_DATE);
    }
}
