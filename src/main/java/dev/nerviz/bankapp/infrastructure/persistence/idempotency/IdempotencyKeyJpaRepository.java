package dev.nerviz.bankapp.infrastructure.persistence.idempotency;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    /**
     * Deletes at most {@code limit} rows whose {@code expires_at} is strictly before
     * {@code cutoff}. {@code FOR UPDATE SKIP LOCKED} in the sub-select lets two concurrent
     * passes (and a concurrent {@code claim}/{@code reclaim}) run without waiting on each
     * other's rows. {@code @Transactional} on this method is what makes each call its own
     * transaction — the port's "commits on its own". No {@code ORDER BY}: every selected row
     * is deleted, so a sort would only defeat {@code LIMIT}'s early stop on the index.
     */
    @Modifying
    @Transactional
    @Query(value = """
                    DELETE FROM idempotency_keys
                    WHERE idempotency_key IN (
                        SELECT idempotency_key
                        FROM idempotency_keys
                        WHERE expires_at < :cutoff
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED
                    )
                    """, nativeQuery = true)
    int deleteExpiredBatch(Instant cutoff, int limit);
}
