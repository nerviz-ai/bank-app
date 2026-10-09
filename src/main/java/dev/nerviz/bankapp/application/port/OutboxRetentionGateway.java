package dev.nerviz.bankapp.application.port;

import java.time.Instant;

/** Output port, kind persistence — implemented by the same store as {@link OutboxRelayGateway}. */
public interface OutboxRetentionGateway {

    /**
     * Commits on its own. Deletes at most {@code limit} PUBLISHED rows whose publish time is
     * before {@code cutoff} and returns how many it deleted. Never a pending row, never a
     * dead-lettered one; skips rows a concurrent transaction holds, so every replica can run it
     * at once.
     */
    int deletePublishedBefore(Instant cutoff, int limit);
}
