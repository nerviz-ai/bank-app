package dev.nerviz.bankapp.infrastructure.persistence.outbox;

import dev.nerviz.bankapp.application.port.OutboxFailure;
import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every state change is one statement; nothing loads the entity to mutate it. No
 * {@code clearAutomatically}: this adapter never holds a managed copy of a row it updates.
 */
interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * The lease: one statement selects the due pending rows with {@code FOR UPDATE SKIP LOCKED}
     * and stamps {@code claimed_until}, so two instances never take the same row and the lease
     * commits before any send. Served by {@code ix_outbox_events_pending}.
     */
    @Query(nativeQuery = true, value = """
                    UPDATE outbox_events
                       SET claimed_until = :leaseUntil
                     WHERE event_id IN (
                        SELECT event_id
                          FROM outbox_events
                         WHERE published_at IS NULL
                           AND NOT dead_lettered
                           AND next_attempt_at <= :now
                           AND (claimed_until IS NULL OR claimed_until < :now)
                         ORDER BY next_attempt_at, occurred_at
                         LIMIT :limit
                           FOR UPDATE SKIP LOCKED)
                    RETURNING *
                    """)
    List<OutboxEventEntity> claimBatch(Instant now, Instant leaseUntil, int limit);

    @Modifying
    @Query(nativeQuery = true, value = """
                    UPDATE outbox_events
                       SET published_at = :now, claimed_until = NULL, last_error = NULL
                     WHERE event_id = :eventId
                    """)
    int markPublished(UUID eventId, Instant now);

    /**
     * Dead-lettering by exhaustion, attempted first so its row count answers "did this attempt
     * exhaust the row": 1 means it did. Zero means attempts remain and {@link #rescheduleAfterFailure}
     * records one.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
                    UPDATE outbox_events
                       SET attempts = attempts + 1, last_error = :reason, claimed_until = NULL,
                           dead_lettered = true
                     WHERE event_id = :eventId
                       AND NOT dead_lettered
                       AND attempts + 1 >= :maxAttempts
                    """)
    int deadLetterIfExhausted(UUID eventId, String reason, int maxAttempts);

    /**
     * Parameters arrive as the two application carriers and are read with SpEL, which keeps the
     * statement at two parameters. Next attempt at {@code now + min(base × 2^(attempts−1), max)} where {@code attempts} is
     * the count after this failure — written as {@code base × 2^attempts} over the count before.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
                    UPDATE outbox_events
                       SET attempts = attempts + 1, last_error = :#{#failure.reason()}, claimed_until = NULL,
                           next_attempt_at = CAST(:#{#failure.failedAt()} AS timestamptz)
                               + LEAST(:#{#policy.backoffBase().toMillis()}
                                           * power(2::double precision, attempts::double precision),
                                       :#{#policy.backoffMax().toMillis()}) * interval '1 millisecond'
                     WHERE event_id = :#{#failure.eventId()}
                       AND NOT dead_lettered
                    """)
    int rescheduleAfterFailure(@Param("failure") OutboxFailure failure, @Param("policy") OutboxRetryPolicy policy);

    @Modifying
    @Query(nativeQuery = true, value = """
                    UPDATE outbox_events
                       SET dead_lettered = true, last_error = :reason, claimed_until = NULL
                     WHERE event_id = :eventId
                    """)
    int markDeadLettered(UUID eventId, String reason);

    /** Scalar {@code min}, served by the same partial index as the claim. */
    @Query("""
            select min(e.occurredAt) from OutboxEventEntity e
             where e.publishedAt is null and e.deadLettered = false""")
    Instant oldestPendingOccurredAt();

    /**
     * One bounded batch. {@code SKIP LOCKED} lets every replica prune at once without waiting
     * on each other. A pending or dead-lettered row has no {@code published_at} and is never
     * selected.
     */
    @Modifying
    @Query(nativeQuery = true, value = """
                    DELETE FROM outbox_events
                     WHERE event_id IN (
                        SELECT event_id
                          FROM outbox_events
                         WHERE published_at < :cutoff
                         ORDER BY published_at
                         LIMIT :limit
                           FOR UPDATE SKIP LOCKED)
                    """)
    int deletePublishedBefore(Instant cutoff, int limit);
}
