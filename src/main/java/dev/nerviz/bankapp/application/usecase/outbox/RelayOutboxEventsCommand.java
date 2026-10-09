package dev.nerviz.bankapp.application.usecase.outbox;

import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Duration;

/**
 * Input for {@link RelayOutboxEventsUseCase}. Built by the relay's trigger from
 * {@code app.outbox.*}, never from outside input — the guards only reject a misconfigured
 * trigger. {@code lease} must outlive one pass; the values are the persistence design's.
 */
public record RelayOutboxEventsCommand(int batchSize, Duration lease, OutboxRetryPolicy retryPolicy) {

    public RelayOutboxEventsCommand {
        if (batchSize < 1) {
            throw new ValidationException("OUTBOX_BATCH_SIZE_INVALID", "batchSize must be at least 1");
        }
        if (lease == null || lease.isNegative() || lease.isZero()) {
            throw new ValidationException("OUTBOX_LEASE_INVALID", "lease must be positive");
        }
        if (retryPolicy == null) {
            throw new ValidationException("OUTBOX_RETRY_POLICY_REQUIRED", "retryPolicy is required");
        }
    }
}
