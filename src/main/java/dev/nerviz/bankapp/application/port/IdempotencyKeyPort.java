package dev.nerviz.bankapp.application.port;

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
}
