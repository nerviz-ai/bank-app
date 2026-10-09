package dev.nerviz.bankapp.application.usecase.outbox;

import dev.nerviz.bankapp.application.port.OutboxRetentionGateway;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Concrete use case — this blueprint's clean architecture has no separate input interface.
 *
 * <p>No {@code @Transactional}: each {@link OutboxRetentionGateway#deletePublishedBefore} call
 * commits on its own, so a failure halfway keeps every batch already deleted and the next run
 * picks up the rest.
 */
@Service
public class PruneOutboxEventsUseCase {

    private final OutboxRetentionGateway retentionGateway;
    private final Clock clock;

    public PruneOutboxEventsUseCase(OutboxRetentionGateway retentionGateway, Clock clock) {
        this.retentionGateway = retentionGateway;
        this.clock = clock;
    }

    /**
     * Deletes published rows older than the retention window, in bounded batches, until one
     * batch comes back short. The cutoff is read once so a long run never chases rows published
     * while it runs.
     *
     * @return the total number of rows deleted across every batch
     */
    public long prunePublished(PruneOutboxEventsCommand command) {
        Instant cutoff = clock.instant().minus(command.retention());
        long total = 0;
        int deleted;
        do {
            deleted = retentionGateway.deletePublishedBefore(cutoff, command.batchSize());
            total += deleted;
        } while (deleted == command.batchSize());
        return total;
    }
}
