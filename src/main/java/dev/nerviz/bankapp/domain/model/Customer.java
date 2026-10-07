package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Aggregate root. Boundary: the customer alone — no reference to another aggregate.
 * {@code register} runs the date rules against today, computed from the injected
 * {@link Clock}; {@code rehydrate} only re-runs the structural invariants already
 * satisfied when the row was written.
 */
public record Customer(
        CustomerId id, String name, SecurityNumber securityNumber, LocalDate birthDate, Instant registeredAt) {

    private static final int NAME_MIN_LENGTH = 2;
    private static final int NAME_MAX_LENGTH = 120;
    private static final int MAX_AGE_YEARS = 130;
    private static final int MINIMUM_AGE_YEARS = 18;

    public Customer {
        if (id == null) {
            throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
        }
        name = requireValidName(name);
        if (securityNumber == null) {
            throw new ValidationException("SECURITY_NUMBER_REQUIRED", "security number is required");
        }
        if (birthDate == null) {
            throw new ValidationException("BIRTH_DATE_REQUIRED", "birth date is required");
        }
        if (registeredAt == null) {
            throw new ValidationException("REGISTERED_AT_REQUIRED", "registration instant is required");
        }
    }

    /** Creation. Runs the date rules against today and stamps {@code registeredAt}. */
    public static Customer register(
            CustomerId id, String name, SecurityNumber securityNumber, LocalDate birthDate, Clock clock) {
        if (birthDate == null) {
            throw new ValidationException("BIRTH_DATE_REQUIRED", "birth date is required");
        }
        LocalDate today = LocalDate.now(clock);
        requireNotInFuture(birthDate, today);
        requireNotTooOld(birthDate, today);
        requireOfAge(birthDate, today);
        return new Customer(id, name, securityNumber, birthDate, clock.instant());
    }

    /** Reconstruction from already-persisted data — used by the persistence adapter. */
    public static Customer rehydrate(
            CustomerId id, String name, SecurityNumber securityNumber, LocalDate birthDate, Instant registeredAt) {
        return new Customer(id, name, securityNumber, birthDate, registeredAt);
    }

    private static void requireNotInFuture(LocalDate birthDate, LocalDate today) {
        if (birthDate.isAfter(today)) {
            throw new ValidationException("BIRTH_DATE_IN_FUTURE", "birth date cannot be in the future");
        }
    }

    private static void requireNotTooOld(LocalDate birthDate, LocalDate today) {
        if (!birthDate.plusYears(MAX_AGE_YEARS + 1).isAfter(today)) {
            throw new ValidationException("BIRTH_DATE_TOO_OLD", "birth date exceeds the maximum age");
        }
    }

    private static void requireOfAge(LocalDate birthDate, LocalDate today) {
        if (!birthDate.plusYears(MINIMUM_AGE_YEARS).isBefore(today)) {
            throw new BusinessRuleViolationException("CUSTOMER_UNDERAGE", "customer must be over 18 years old");
        }
    }

    private static String requireValidName(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new ValidationException("CUSTOMER_NAME_REQUIRED", "name is required");
        }
        String trimmed = candidate.trim();
        if (trimmed.length() < NAME_MIN_LENGTH || trimmed.length() > NAME_MAX_LENGTH) {
            throw new ValidationException("CUSTOMER_NAME_LENGTH", "name must be between 2 and 120 characters");
        }
        return trimmed;
    }

    @Override
    public String toString() {
        return "Customer[id=" + id + ", name=" + name + ", securityNumber=" + securityNumber
                + ", birthDate=***, registeredAt=" + registeredAt + "]";
    }
}
