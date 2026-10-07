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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void deleteExpiredRemovesOnlyRowsBeforeCutoff() {
        Instant cutoff = Instant.parse("2026-01-15T10:00:00Z");
        UUID beforeCutoff = insertRowExpiringAt(cutoff.minusSeconds(1));
        UUID atCutoff = insertRowExpiringAt(cutoff);
        UUID afterCutoff = insertRowExpiringAt(cutoff.plus(Duration.ofHours(1)));

        int deleted = store.deleteExpired(cutoff, 10);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.existsById(beforeCutoff)).isFalse();
        assertThat(repository.existsById(atCutoff)).isTrue();
        assertThat(repository.existsById(afterCutoff)).isTrue();
    }

    @Test
    void deleteExpiredDeletesAtMostLimit() {
        Instant cutoff = Instant.parse("2026-01-15T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            insertRowExpiringAt(cutoff.minusSeconds(1));
        }

        int deleted = store.deleteExpired(cutoff, 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.count()).isEqualTo(3);
    }

    /**
     * A second thread holds {@code SELECT ... FOR UPDATE} on one expired row, inside its own
     * open transaction, while the test thread calls {@code deleteExpired}. Both latches are
     * bounded: a regression to a blocking {@code FOR UPDATE} fails the assertion instead of
     * hanging the build ({@code 40-testes.md} § 2).
     */
    @Test
    void deleteExpiredSkipsRowLockedByAnotherTransaction() throws InterruptedException {
        Instant cutoff = Instant.parse("2026-01-15T10:00:00Z");
        UUID lockedKey = insertRowExpiringAt(cutoff.minusSeconds(1));
        UUID freeKeyA = insertRowExpiringAt(cutoff.minusSeconds(1));
        UUID freeKeyB = insertRowExpiringAt(cutoff.minusSeconds(1));
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        Thread holder = new Thread(() -> transactions.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT idempotency_key FROM idempotency_keys WHERE idempotency_key = ? FOR UPDATE",
                    UUID.class,
                    lockedKey);
            rowLocked.countDown();
            await(releaseLock);
        }));
        holder.start();
        assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();

        int deleted = store.deleteExpired(cutoff, 10);
        releaseLock.countDown();
        holder.join(Duration.ofSeconds(5).toMillis());

        assertThat(deleted).isEqualTo(2);
        assertThat(repository.existsById(lockedKey)).isTrue();
        assertThat(repository.existsById(freeKeyA)).isFalse();
        assertThat(repository.existsById(freeKeyB)).isFalse();
    }

    /**
     * Two threads released together by a latch, over N expired and M unexpired rows: both
     * return normally (no exception propagates from either {@link Future#get}), the counts sum
     * to N, and the M unexpired rows remain untouched.
     */
    @Test
    void concurrentDeleteExpiredNeitherFailsNorTouchesUnexpired() throws Exception {
        Instant cutoff = Instant.parse("2026-01-15T10:00:00Z");
        int expiredCount = 6;
        int unexpiredCount = 4;
        for (int i = 0; i < expiredCount; i++) {
            insertRowExpiringAt(cutoff.minusSeconds(1));
        }
        for (int i = 0; i < unexpiredCount; i++) {
            insertRowExpiringAt(cutoff.plus(Duration.ofHours(1)));
        }
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> pass = () -> {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            return store.deleteExpired(cutoff, expiredCount);
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(pass);
            Future<Integer> second = executor.submit(pass);
            start.countDown();

            int deletedByFirst = first.get(10, TimeUnit.SECONDS);
            int deletedBySecond = second.get(10, TimeUnit.SECONDS);

            assertThat(deletedByFirst + deletedBySecond).isEqualTo(expiredCount);
            assertThat(repository.count()).isEqualTo(unexpiredCount);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void deleteExpiredRefusesActiveTransaction() {
        Instant cutoff = Instant.parse("2026-01-15T10:00:00Z");

        assertThatThrownBy(() -> deleteExpiredInsideTransaction(cutoff)).isInstanceOf(IllegalStateException.class);
    }

    private void deleteExpiredInsideTransaction(Instant cutoff) {
        transactions.executeWithoutResult(status -> store.deleteExpired(cutoff, 10));
    }

    private UUID insertRowExpiringAt(Instant expiresAt) {
        UUID key = UUID.randomUUID();
        repository.saveAndFlush(new IdempotencyKeyEntity(request(key, HASH_A), expiresAt));
        return key;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch not released in time");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static IdempotencyRequest request(UUID key, String bodyHash) {
        return new IdempotencyRequest(
                key, "POST /api/v1/customers", "anonymous", bodyHash, Instant.parse("2026-01-15T10:00:00Z"));
    }
}
