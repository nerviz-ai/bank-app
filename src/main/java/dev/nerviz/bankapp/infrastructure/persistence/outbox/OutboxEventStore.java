package dev.nerviz.bankapp.infrastructure.persistence.outbox;

import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxFailure;
import dev.nerviz.bankapp.application.port.OutboxRelayGateway;
import dev.nerviz.bankapp.application.port.OutboxRetentionGateway;
import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Both outbox gateways over one table. {@code @Transactional} sits only where no use case's
 * transaction covers the write — the relay's claim and per-row marks, one bounded batch of a
 * prune ({@.claude/rules/persistence.md} § Boundary). {@link #append} is the opposite: it
 * joins the caller's transaction and refuses to run outside one. {@code now} is always the
 * caller's, from the application {@code Clock}.
 *
 * <p>{@code last_error} holds the broker's message only, truncated: it must never carry the
 * payload, which has personal data in clear.
 */
@Component
class OutboxEventStore implements OutboxRelayGateway, OutboxRetentionGateway {

    private static final int MAX_ERROR_LENGTH = 1000;
    private static final String UNKNOWN_ERROR = "unknown";

    private final OutboxEventJpaRepository repository;

    OutboxEventStore(OutboxEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(OutboxEventRecord event) {
        repository.saveAndFlush(new OutboxEventEntity(
                event.eventId(), event.aggregateId(), event.eventType(), event.payload(), event.occurredAt()));
    }

    /** Own transaction: the lease commits before this returns, ahead of every send. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEventRecord> claimPending(int limit, Instant now, Duration lease) {
        return repository.claimBatch(now, now.plus(lease), limit).stream()
                .sorted(Comparator.comparing(OutboxEventEntity::getOccurredAt))
                .map(OutboxEventStore::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID eventId, Instant now) {
        repository.markPublished(eventId, now);
    }

    /** Two statements in one transaction: exhaustion first, so its row count is the answer. */
    @Override
    @Transactional
    public boolean recordFailure(OutboxFailure failure, OutboxRetryPolicy policy) {
        OutboxFailure bounded = new OutboxFailure(failure.eventId(), truncate(failure.reason()), failure.failedAt());
        if (repository.deadLetterIfExhausted(bounded.eventId(), bounded.reason(), policy.maxAttempts()) == 1) {
            return true;
        }
        repository.rescheduleAfterFailure(bounded, policy);
        return false;
    }

    @Override
    @Transactional
    public void markDeadLettered(UUID eventId, String reason) {
        repository.markDeadLettered(eventId, truncate(reason));
    }

    @Override
    public Optional<Duration> oldestPendingAge(Instant now) {
        return Optional.ofNullable(repository.oldestPendingOccurredAt()).map(oldest -> Duration.between(oldest, now));
    }

    /** One bounded batch per call, in its own transaction. */
    @Override
    @Transactional
    public int deletePublishedBefore(Instant cutoff, int limit) {
        return repository.deletePublishedBefore(cutoff, limit);
    }

    private static OutboxEventRecord toRecord(OutboxEventEntity entity) {
        return new OutboxEventRecord(
                entity.getEventId(),
                entity.getAggregateId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getOccurredAt(),
                entity.getAttempts());
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return UNKNOWN_ERROR;
        }
        return reason.length() <= MAX_ERROR_LENGTH ? reason : reason.substring(0, MAX_ERROR_LENGTH);
    }
}
