package dev.nerviz.bankapp.application.port;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port, kind persistence — the outbox's only view for the append side and the relay.
 * {@code now} always comes from the application {@code Clock}, so lease and backoff are
 * deterministic in tests. Any {@code DataAccessException} stays inside the adapter's
 * boundary rules; no framework type is in a signature.
 */
public interface OutboxRelayGateway {

    /**
     * Writes a row, pending, due at {@code occurredAt}. Joins the caller's transaction and
     * fails loudly when there is none — an append outside it recreates the gap the outbox
     * exists to close.
     */
    void append(OutboxEventRecord event);

    /**
     * Leases up to {@code limit} due, pending rows until {@code now + lease} and returns them
     * in occurrence order. The lease commits before this returns, so the sends that follow hold
     * no row lock, and a crashed instance's rows return after the lease.
     */
    List<OutboxEventRecord> claimPending(int limit, Instant now, Duration lease);

    void markPublished(UUID eventId, Instant now);

    /**
     * Spends an attempt, clears the lease and schedules the next one by the policy's backoff.
     * Returns whether THIS call is the one that dead-lettered the row (attempt ceiling reached):
     * the relay cannot see the row's attempts, and a counter that misses the main loss path
     * measures nothing.
     */
    boolean recordFailure(OutboxFailure failure, OutboxRetryPolicy policy);

    /**
     * Dead-letters the row now, without spending attempts: a failure retrying cannot fix. A
     * named operation, not {@code recordFailure} with a ceiling of zero.
     */
    void markDeadLettered(UUID eventId, String reason);

    /** Age of the oldest pending, non-dead-lettered row; empty when nothing is pending. */
    Optional<Duration> oldestPendingAge(Instant now);
}
