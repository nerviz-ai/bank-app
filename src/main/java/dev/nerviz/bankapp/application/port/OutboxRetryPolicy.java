package dev.nerviz.bankapp.application.port;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Duration;

/**
 * Pacing of a failed row: the attempt ceiling and the per-row exponential backoff
 * ({@code backoffBase × 2^(attempts−1)}, capped at {@code backoffMax}). Built by the relay's
 * trigger from {@code app.outbox.*}; the guards only reject a misconfigured trigger.
 */
public record OutboxRetryPolicy(int maxAttempts, Duration backoffBase, Duration backoffMax) {

    public OutboxRetryPolicy {
        if (maxAttempts < 1) {
            throw new ValidationException("OUTBOX_MAX_ATTEMPTS_INVALID", "maxAttempts must be at least 1");
        }
        if (isNotPositive(backoffBase)) {
            throw new ValidationException("OUTBOX_BACKOFF_BASE_INVALID", "backoffBase must be positive");
        }
        if (isNotPositive(backoffMax) || backoffMax.compareTo(backoffBase) < 0) {
            throw new ValidationException(
                    "OUTBOX_BACKOFF_MAX_INVALID", "backoffMax must be positive and not below backoffBase");
        }
    }

    private static boolean isNotPositive(Duration value) {
        return value == null || value.isNegative() || value.isZero();
    }
}
