package dev.nerviz.bankapp.infrastructure.persistence.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.application.port.IdempotencyClaim;
import dev.nerviz.bankapp.application.port.IdempotencyKeyPort;
import dev.nerviz.bankapp.application.port.IdempotencyRequest;
import dev.nerviz.bankapp.application.port.StoredResponse;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.testcontainers.DockerClientFactory;

/**
 * Two-transaction shape and primary-key collision only exist against the real database.
 * {@code @Transactional(NOT_SUPPORTED)} overrides {@code @DataJpaTest}'s default
 * per-method transaction: {@code claim} and {@code release} require no transaction
 * active, which a wrapping test transaction would defeat. Rows are deleted by hand
 * between tests instead of relying on rollback.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, IdempotencyKeyStore.class})
@EnabledIf("dockerAvailable")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class IdempotencyKeyStoreIT {

    /** {@code body_hash} is {@code char(64)}: a shorter literal gets space-padded by Postgres. */
    private static final String HASH_A = "a".repeat(64);

    private static final String HASH_B = "b".repeat(64);

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private IdempotencyKeyPort store;

    @Autowired
    private TransactionOperations transactions;

    @Autowired
    private IdempotencyKeyJpaRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void claimsNewKey() {
        IdempotencyRequest request = request(UUID.randomUUID(), HASH_A);

        IdempotencyClaim claim = store.claim(request);

        assertThat(claim).isInstanceOf(IdempotencyClaim.Proceed.class);
    }

    @Test
    void replaysCompletedResponse() {
        UUID key = UUID.randomUUID();
        IdempotencyRequest request = request(key, HASH_A);
        store.claim(request);
        StoredResponse response = new StoredResponse(201, "{\"id\":\"abc\"}", "{}");
        transactions.executeWithoutResult(status -> store.complete(key, response));

        IdempotencyClaim replay = store.claim(request(key, HASH_A));

        assertThat(replay).isInstanceOf(IdempotencyClaim.Replay.class);
        StoredResponse replayed = ((IdempotencyClaim.Replay) replay).response();
        assertThat(replayed.status()).isEqualTo(201);
        assertThat(replayed.body()).isEqualTo("{\"id\":\"abc\"}");
    }

    @Test
    void rejectsSameKeyWithDifferentBody() {
        UUID key = UUID.randomUUID();
        store.claim(request(key, HASH_A));
        IdempotencyRequest differentBody = request(key, HASH_B);

        assertThatThrownBy(() -> store.claim(differentBody))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void rejectsKeyStillInProgress() {
        UUID key = UUID.randomUUID();
        store.claim(request(key, HASH_A));
        IdempotencyRequest retryStillInLease = request(key, HASH_A);

        assertThatThrownBy(() -> store.claim(retryStillInLease))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("IDEMPOTENCY_KEY_IN_PROGRESS");
    }

    @Test
    void reclaimsKeyAfterLeaseExpires() {
        UUID key = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        store.claim(new IdempotencyRequest(key, "POST /api/v1/customers", "anonymous", HASH_A, now));
        IdempotencyRequest afterLease = new IdempotencyRequest(
                key, "POST /api/v1/customers", "anonymous", HASH_A, now.plus(Duration.ofMinutes(6)));

        IdempotencyClaim reclaimed = store.claim(afterLease);

        assertThat(reclaimed).isInstanceOf(IdempotencyClaim.Proceed.class);
    }

    @Test
    void releaseFreesKeyForRetry() {
        UUID key = UUID.randomUUID();
        store.claim(request(key, HASH_A));
        store.release(key);

        IdempotencyClaim afterRelease = store.claim(request(key, HASH_A));

        assertThat(afterRelease).isInstanceOf(IdempotencyClaim.Proceed.class);
    }

    private static IdempotencyRequest request(UUID key, String bodyHash) {
        return new IdempotencyRequest(
                key, "POST /api/v1/customers", "anonymous", bodyHash, Instant.parse("2026-01-15T10:00:00Z"));
    }
}
