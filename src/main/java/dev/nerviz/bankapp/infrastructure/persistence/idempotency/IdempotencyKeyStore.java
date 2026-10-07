package dev.nerviz.bankapp.infrastructure.persistence.idempotency;

import dev.nerviz.bankapp.application.port.IdempotencyClaim;
import dev.nerviz.bankapp.application.port.IdempotencyKeyPort;
import dev.nerviz.bankapp.application.port.IdempotencyRequest;
import dev.nerviz.bankapp.application.port.StoredResponse;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Transactional mirror of the REST layer's {@code IdempotencyKeyInterceptor}
 * ({@.claude/rules/api-rest.md} § Idempotency). {@code claim} and {@code release} commit on
 * their own, outside any business transaction; {@code complete} joins it.
 */
@Component
class IdempotencyKeyStore implements IdempotencyKeyPort {

    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration IN_PROGRESS_LEASE = Duration.ofMinutes(5);

    private final IdempotencyKeyJpaRepository repository;

    IdempotencyKeyStore(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public IdempotencyClaim claim(IdempotencyRequest request) {
        requireNoTransaction("claim");
        try {
            repository.saveAndFlush(
                    new IdempotencyKeyEntity(request, request.now().plus(TTL)));
            return new IdempotencyClaim.Proceed();
        } catch (DataIntegrityViolationException cause) {
            IdempotencyKeyEntity existing = repository.findById(request.key()).orElseThrow(() -> cause);
            return resolveCollision(existing, request);
        }
    }

    @Override
    public void complete(UUID key, StoredResponse response) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("complete must join the business transaction");
        }
        repository.findById(key).orElseThrow().applyResponse(response.status(), response.body(), response.headers());
    }

    @Override
    public void release(UUID key) {
        requireNoTransaction("release");
        repository.deleteById(key);
    }

    @Override
    public int deleteExpired(Instant cutoff, int limit) {
        requireNoTransaction("deleteExpired");
        return repository.deleteExpiredBatch(cutoff, limit);
    }

    private IdempotencyClaim resolveCollision(IdempotencyKeyEntity existing, IdempotencyRequest request) {
        if (!existing.getBodyHash().equals(request.bodyHash())) {
            throw new ConflictException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency-Key " + request.key() + " was already used with a different request body");
        }
        if (existing.getStatus() == IdempotencyStatus.COMPLETED) {
            return new IdempotencyClaim.Replay(new StoredResponse(
                    existing.getResponseStatus(), existing.getResponseBody(), existing.getResponseHeaders()));
        }
        if (existing.getClaimedAt().plus(IN_PROGRESS_LEASE).isAfter(request.now())) {
            throw inProgress(request.key());
        }
        return reclaim(existing, request);
    }

    private IdempotencyClaim reclaim(IdempotencyKeyEntity stale, IdempotencyRequest request) {
        stale.reclaim(request.now());
        try {
            repository.saveAndFlush(stale);
            return new IdempotencyClaim.Proceed();
        } catch (OptimisticLockingFailureException lostRace) {
            throw inProgress(request.key());
        }
    }

    private static ConflictException inProgress(UUID key) {
        return new ConflictException(
                "IDEMPOTENCY_KEY_IN_PROGRESS", "Request with Idempotency-Key " + key + " is still being processed");
    }

    private static void requireNoTransaction(String operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(operation + " must commit on its own, outside the business transaction");
        }
    }
}
