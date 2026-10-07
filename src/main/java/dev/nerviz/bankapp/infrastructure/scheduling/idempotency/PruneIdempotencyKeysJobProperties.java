package dev.nerviz.bankapp.infrastructure.scheduling.idempotency;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.jobs.prune-idempotency-keys.batch-size} and {@code .max-batches} only.
 * {@code enabled}, {@code interval} and {@code initial-delay} are read directly by the
 * placeholders in {@link PruneExpiredIdempotencyKeysJob}'s annotations and are never bound a
 * second time here: a property read by an annotation placeholder is resolved before any bean
 * exists, so binding it into this record too would be a second, unread, copy of the same value
 * (UC-002-prune-expired-idempotency-keys spec § 3.6, {@.claude/rules/scheduling.md} § Triggers).
 */
@ConfigurationProperties(prefix = "app.jobs.prune-idempotency-keys")
@Validated
record PruneIdempotencyKeysJobProperties(
        @Min(1) int batchSize, @Min(1) int maxBatches) {}
