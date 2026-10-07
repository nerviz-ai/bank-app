package dev.nerviz.bankapp.application.usecase.idempotency;

import dev.nerviz.bankapp.domain.exception.ValidationException;

/**
 * Input for {@link PruneExpiredIdempotencyKeysUseCase}. Built by the scheduled trigger from
 * its job properties, never from outside input — the bounds below only guard a misconfigured
 * trigger ({@.claude/rules/value-objects.md}: a bound on a single primitive used in one place
 * does not earn a value object).
 */
public record PruneExpiredIdempotencyKeysCommand(int batchSize, int maxBatches) {

    public PruneExpiredIdempotencyKeysCommand {
        if (batchSize < 1) {
            throw new ValidationException("PRUNE_BATCH_SIZE_INVALID", "batchSize must be at least 1");
        }
        if (maxBatches < 1) {
            throw new ValidationException("PRUNE_MAX_BATCHES_INVALID", "maxBatches must be at least 1");
        }
    }
}
