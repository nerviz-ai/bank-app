package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.ConflictException;
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
        CustomerId id, String name, CustomerIdentity identity, Instant registeredAt, CustomerStatus status) {

    public static final String ANONYMIZED_NAME = "ANONYMIZED";

    private static final int NAME_MIN_LENGTH = 2;
    private static final int NAME_MAX_LENGTH = 120;
    private static final int MAX_AGE_YEARS = 130;
    private static final int MINIMUM_AGE_YEARS = 18;

    public Customer {
        if (id == null) {
            throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
        }
        name = requireValidName(name);
        if (identity == null) {
            throw new ValidationException("CUSTOMER_IDENTITY_REQUIRED", "customer identity is required");
        }
        if (identity instanceof CustomerIdentity.Anonymized && !ANONYMIZED_NAME.equals(name)) {
            throw new ValidationException(
                    "CUSTOMER_ANONYMIZED_NAME_MISMATCH", "an anonymized customer must carry the placeholder name");
        }
        if (registeredAt == null) {
            throw new ValidationException("REGISTERED_AT_REQUIRED", "registration instant is required");
        }
        if (status == null) {
            throw new ValidationException("CUSTOMER_STATUS_REQUIRED", "customer status is required");
        }
    }

    /**
     * Creation. Runs the date rules against today, stamps {@code registeredAt} and starts the
     * customer in {@link CustomerStatus#KYC_IN_PROGRESS}: no parameter lets the caller choose
     * another status.
     */
    public static Customer register(
            CustomerId id, String name, SecurityNumber securityNumber, LocalDate birthDate, Clock clock) {
        if (birthDate == null) {
            throw new ValidationException("BIRTH_DATE_REQUIRED", "birth date is required");
        }
        LocalDate today = LocalDate.now(clock);
        requireNotInFuture(birthDate, today);
        requireNotTooOld(birthDate, today);
        requireOfAge(birthDate, today);
        return new Customer(
                id,
                name,
                CustomerIdentity.identified(securityNumber, birthDate),
                clock.instant(),
                CustomerStatus.KYC_IN_PROGRESS);
    }

    /** Reconstruction from already-persisted data — used by the persistence adapter. */
    public static Customer rehydrate(
            CustomerId id, String name, CustomerIdentity identity, Instant registeredAt, CustomerStatus status) {
        return new Customer(id, name, identity, registeredAt, status);
    }

    /**
     * The only way out of {@link CustomerStatus#KYC_IN_PROGRESS}. Returns the next state, never
     * mutates. The verdict already applied returns {@code this} (duplicate, no-op); the opposite
     * one on a final status is a conflict — the first verdict stands.
     *
     * @throws ValidationException {@code KYC_VERDICT_REQUIRED} — {@code verdict} is {@code null}
     * @throws ConflictException {@code KYC_VERDICT_CONFLICT} — the opposite verdict was recorded
     */
    public Customer recordKycVerdict(KycVerdict verdict) {
        if (verdict == null) {
            throw new ValidationException("KYC_VERDICT_REQUIRED", "kyc verdict is required");
        }
        CustomerStatus target = verdict.resultingStatus();
        if (status == CustomerStatus.KYC_IN_PROGRESS) {
            return new Customer(id, name, identity, registeredAt, target);
        }
        if (status == target) {
            return this;
        }
        throw new ConflictException(
                "KYC_VERDICT_CONFLICT", "customer already has a final kyc status that conflicts with the verdict");
    }

    /**
     * Erases the personal data and keeps the row. Returns the next state, never mutates. An
     * already-anonymized customer returns {@code this} — checked before the status rule, so the
     * first {@code anonymizedAt} stays. Status is not touched.
     *
     * @throws BusinessRuleViolationException {@code CUSTOMER_KYC_IN_PROGRESS} — the KYC status is
     *     not final yet
     */
    public Customer anonymize(Clock clock) {
        if (isAnonymized()) {
            return this;
        }
        if (status == CustomerStatus.KYC_IN_PROGRESS) {
            throw new BusinessRuleViolationException(
                    "CUSTOMER_KYC_IN_PROGRESS", "customer cannot be anonymized while KYC is in progress");
        }
        return new Customer(id, ANONYMIZED_NAME, CustomerIdentity.anonymized(clock.instant()), registeredAt, status);
    }

    public boolean isAnonymized() {
        return identity instanceof CustomerIdentity.Anonymized;
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
        return "Customer[id=" + id + ", name=***, identity=" + identity + ", registeredAt=" + registeredAt + ", status="
                + status + "]";
    }
}
