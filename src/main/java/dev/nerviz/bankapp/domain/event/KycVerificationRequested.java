package dev.nerviz.bankapp.domain.event;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A customer was registered and the KYC application has to verify them. Built only from an
 * already-valid {@link Customer}, so the presence checks are a programming-error guard, not a
 * rule a request can break — one {@code errorCode} covers the five fields.
 *
 * <p>The content carries {@code securityNumber} and {@code birthDate} in clear on purpose: the
 * receiver verifies exactly those values. {@code toString()} masks both, for log lines only.
 */
public record KycVerificationRequested(
        CustomerId customerId, String name, SecurityNumber securityNumber, LocalDate birthDate, Instant occurredAt) {

    private static final String FIELD_REQUIRED = "KYC_REQUEST_FIELD_REQUIRED";

    public KycVerificationRequested {
        require(customerId, "customerId");
        require(name, "name");
        require(securityNumber, "securityNumber");
        require(birthDate, "birthDate");
        require(occurredAt, "occurredAt");
    }

    /** {@code occurredAt} is the creation instant, already read from the injected {@code Clock}. */
    public static KycVerificationRequested of(Customer customer) {
        return new KycVerificationRequested(
                customer.id(),
                customer.name(),
                customer.securityNumber(),
                customer.birthDate(),
                customer.registeredAt());
    }

    private static void require(Object value, String field) {
        if (value == null) {
            throw new ValidationException(FIELD_REQUIRED, field + " is required");
        }
    }

    @Override
    public String toString() {
        return "KycVerificationRequested[customerId=" + customerId + ", name=" + name + ", securityNumber="
                + securityNumber + ", birthDate=***, occurredAt=" + occurredAt + "]";
    }
}
