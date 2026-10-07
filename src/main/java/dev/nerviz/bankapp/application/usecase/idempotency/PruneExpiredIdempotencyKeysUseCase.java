package dev.nerviz.bankapp.application.usecase.idempotency;

import dev.nerviz.bankapp.application.port.IdempotencyKeyPort;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * Concrete use case — this blueprint's clean architecture has no separate input interface
 * ({@.claude/rules/naming.md} § Architecture vocabulary).
 *
 * <p>No {@code @Transactional} on {@link #prune}: each {@link IdempotencyKeyPort#deleteExpired}
 * call commits on its own, the same shape the port already uses for {@code claim} and
 * {@code release}. A run-wide transaction would hold the deleted rows' locks for the whole run
 * and lose every batch on a late failure — the opposite of what this use case exists to fix
 * ({@.claude/rules/architecture-ddd.md} § Application: the transaction boundary here is the
 * port call, not the use case method).
 */
@Service
public class PruneExpiredIdempotencyKeysUseCase {

    private final IdempotencyKeyPort idempotencyKeyPort;
    private final Clock clock;

    public PruneExpiredIdempotencyKeysUseCase(IdempotencyKeyPort idempotencyKeyPort, Clock clock) {
        this.idempotencyKeyPort = idempotencyKeyPort;
        this.clock = clock;
    }

    /**
     * Deletes every {@code idempotency_keys} row expired before this call started, in bounded
     * batches. The cutoff is read once so a long run never chases rows that expire while it
     * runs.
     *
     * @return the total number of rows deleted across every batch
     */
    public long prune(PruneExpiredIdempotencyKeysCommand command) {
        Instant cutoff = Instant.now(clock);
        long total = 0;
        int batches = 0;
        int deletedInBatch;
        do {
            deletedInBatch = idempotencyKeyPort.deleteExpired(cutoff, command.batchSize());
            total += deletedInBatch;
            batches++;
        } while (deletedInBatch == command.batchSize() && batches < command.maxBatches());
        return total;
    }
}
