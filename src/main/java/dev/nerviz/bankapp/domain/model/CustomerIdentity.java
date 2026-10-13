package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Who the customer is, or the fact that this is no longer known. {@link Identified} holds the
 * personal data; {@link Anonymized} holds the instant it was erased. The pair is present together
 * or absent together, and the instant exists exactly when the pair is absent — one sealed type
 * states the three facts with no invalid combination left. {@code Anonymized} is final: there is
 * no way back.
 */
public sealed interface CustomerIdentity {

    static CustomerIdentity identified(SecurityNumber securityNumber, LocalDate birthDate) {
        return new Identified(securityNumber, birthDate);
    }

    static CustomerIdentity anonymized(Instant anonymizedAt) {
        return new Anonymized(anonymizedAt);
    }

    /** Both values are personal data; {@code toString()} masks both, for log lines only. */
    record Identified(SecurityNumber securityNumber, LocalDate birthDate) implements CustomerIdentity {

        public Identified {
            if (securityNumber == null) {
                throw new ValidationException("SECURITY_NUMBER_REQUIRED", "security number is required");
            }
            if (birthDate == null) {
                throw new ValidationException("BIRTH_DATE_REQUIRED", "birth date is required");
            }
        }

        @Override
        public String toString() {
            return "Identified[securityNumber=" + securityNumber + ", birthDate=***]";
        }
    }

    /** No personal data: only the instant the erasure happened. */
    record Anonymized(Instant anonymizedAt) implements CustomerIdentity {

        public Anonymized {
            if (anonymizedAt == null) {
                throw new ValidationException("ANONYMIZED_AT_REQUIRED", "anonymization instant is required");
            }
        }
    }
}
