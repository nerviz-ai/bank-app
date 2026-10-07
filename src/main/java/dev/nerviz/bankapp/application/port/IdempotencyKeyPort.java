package dev.nerviz.bankapp.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * One instance for the whole application, shared by every route that requires
 * {@code Idempotency-Key} — not one per aggregate.
 */
public interface IdempotencyKeyPort {

    /** Commits on its own. Called with no transaction active. */
    IdempotencyClaim claim(IdempotencyRequest request);

    /** Joins the caller's transaction — the same one as the business effect. */
    void complete(UUID key, StoredResponse response);

    /** Commits on its own. Called after the business transaction rolled back. */
    void release(UUID key);

    /**
     * Commits on its own. Called with no transaction active. Deletes at most {@code limit}
     * rows whose {@code expires_at} is strictly before {@code cutoff}; returns how many it
     * deleted. Safe to run concurrently with itself: two callers never fail each other and
     * never delete a row with {@code expires_at} at or after the cutoff.
     */
    int deleteExpired(Instant cutoff, int limit);
}
