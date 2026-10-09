package dev.nerviz.bankapp.application.usecase.outbox;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Duration;

/**
 * Input for {@link PruneOutboxEventsUseCase}. Built by the prune trigger from
 * {@code app.outbox.*} — the guards only reject a misconfigured trigger.
 */
public record PruneOutboxEventsCommand(Duration retention, int batchSize) {

    public PruneOutboxEventsCommand {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new ValidationException("OUTBOX_RETENTION_INVALID", "retention must be positive");
        }
        if (batchSize < 1) {
            throw new ValidationException("OUTBOX_PRUNE_BATCH_SIZE_INVALID", "batchSize must be at least 1");
        }
    }
}
