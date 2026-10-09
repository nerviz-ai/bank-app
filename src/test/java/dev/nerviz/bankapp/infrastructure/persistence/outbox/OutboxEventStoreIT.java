package dev.nerviz.bankapp.infrastructure.persistence.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxFailure;
import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * SQL, {@code FOR UPDATE SKIP LOCKED}, {@code jsonb} and the lease only fail against the real
 * engine. The store commits on its own (claim, marks, prune), so the class runs outside the
 * test transaction and cleans the table by hand; {@code now} is passed explicitly so lease and
 * backoff are deterministic.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, OutboxEventStore.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@EnabledIf("dockerAvailable")
class OutboxEventStoreIT {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(8);
    private static final OutboxRetryPolicy POLICY =
            new OutboxRetryPolicy(3, Duration.ofSeconds(1), Duration.ofMinutes(5));

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private OutboxEventStore store;

    @Autowired
    private OutboxEventJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanTable() {
        repository.deleteAll();
    }

    @Test
    void appendFailsOutsideTransaction() {
        OutboxEventRecord event = event(NOW.minusSeconds(10));

        assertThatThrownBy(() -> store.append(event)).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void claimLeasesRowsAndHidesThemFromSecondClaim() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));

        List<OutboxEventRecord> first = store.claimPending(10, NOW, LEASE);
        List<OutboxEventRecord> second = store.claimPending(10, NOW.plusSeconds(1), LEASE);

        assertThat(first).extracting(OutboxEventRecord::eventId).containsExactly(appended.eventId());
        assertThat(second).isEmpty();
    }

    @Test
    void expiredLeaseIsClaimableAgain() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));
        store.claimPending(10, NOW, LEASE);

        List<OutboxEventRecord> afterLease =
                store.claimPending(10, NOW.plus(LEASE).plusSeconds(1), LEASE);

        assertThat(afterLease).extracting(OutboxEventRecord::eventId).containsExactly(appended.eventId());
    }

    @Test
    void claimSkipsRowsNotYetDue() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));
        store.recordFailure(new OutboxFailure(appended.eventId(), "broker down", NOW), POLICY);

        List<OutboxEventRecord> beforeBackoff = store.claimPending(10, NOW, LEASE);
        List<OutboxEventRecord> afterBackoff = store.claimPending(10, NOW.plusSeconds(2), LEASE);

        assertThat(beforeBackoff).isEmpty();
        assertThat(afterBackoff).hasSize(1);
    }

    @Test
    void claimReturnsOccurrenceOrder() {
        OutboxEventRecord newer = append(NOW.minusSeconds(5));
        OutboxEventRecord older = append(NOW.minusSeconds(50));
        OutboxEventRecord middle = append(NOW.minusSeconds(20));

        List<OutboxEventRecord> claimed = store.claimPending(10, NOW, LEASE);

        assertThat(claimed)
                .extracting(OutboxEventRecord::eventId)
                .containsExactly(older.eventId(), middle.eventId(), newer.eventId());
    }

    @Test
    void claimRespectsLimit() {
        append(NOW.minusSeconds(30));
        append(NOW.minusSeconds(20));
        append(NOW.minusSeconds(10));

        List<OutboxEventRecord> claimed = store.claimPending(2, NOW, LEASE);

        assertThat(claimed).hasSize(2);
    }

    @Test
    void concurrentClaimsAreDisjoint() throws Exception {
        for (int i = 0; i < 20; i++) {
            append(NOW.minusSeconds(100 - i));
        }
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<List<OutboxEventRecord>> claim = () -> store.claimPending(20, NOW, LEASE);

            Future<List<OutboxEventRecord>> left = executor.submit(claim);
            Future<List<OutboxEventRecord>> right = executor.submit(claim);

            Set<UUID> all = new HashSet<>();
            List<OutboxEventRecord> leftRows = left.get(30, TimeUnit.SECONDS);
            List<OutboxEventRecord> rightRows = right.get(30, TimeUnit.SECONDS);
            leftRows.forEach(row -> all.add(row.eventId()));
            rightRows.forEach(row -> all.add(row.eventId()));
            assertThat(all).hasSize(leftRows.size() + rightRows.size()).hasSize(20);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void recordFailureBacksOffExponentially() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));

        store.recordFailure(new OutboxFailure(appended.eventId(), "first", NOW), POLICY);
        Instant afterFirst =
                repository.findById(appended.eventId()).orElseThrow().getNextAttemptAt();
        store.recordFailure(new OutboxFailure(appended.eventId(), "second", NOW), POLICY);
        OutboxEventEntity afterSecond = repository.findById(appended.eventId()).orElseThrow();

        assertThat(afterFirst).isEqualTo(NOW.plusSeconds(1));
        assertThat(afterSecond.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(2));
        assertThat(afterSecond.getAttempts()).isEqualTo(2);
        assertThat(afterSecond.getClaimedUntil()).isNull();
        assertThat(afterSecond.getLastError()).isEqualTo("second");
    }

    @Test
    void recordFailureCapsTheBackoff() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));
        OutboxRetryPolicy capped = new OutboxRetryPolicy(100, Duration.ofSeconds(1), Duration.ofSeconds(3));

        for (int attempt = 0; attempt < 4; attempt++) {
            store.recordFailure(new OutboxFailure(appended.eventId(), "down", NOW), capped);
        }

        OutboxEventEntity row = repository.findById(appended.eventId()).orElseThrow();
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(3));
    }

    @Test
    void recordFailureDeadLettersAtCeiling() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));

        boolean first = store.recordFailure(new OutboxFailure(appended.eventId(), "one", NOW), POLICY);
        boolean second = store.recordFailure(new OutboxFailure(appended.eventId(), "two", NOW), POLICY);
        boolean third = store.recordFailure(new OutboxFailure(appended.eventId(), "three", NOW), POLICY);

        assertThat(List.of(first, second, third)).containsExactly(false, false, true);
        OutboxEventEntity row = repository.findById(appended.eventId()).orElseThrow();
        assertThat(row.isDeadLettered()).isTrue();
        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(store.claimPending(10, NOW.plus(Duration.ofDays(1)), LEASE)).isEmpty();
    }

    @Test
    void markDeadLetteredStopsTheRowWithoutSpendingAttempts() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));

        store.markDeadLettered(appended.eventId(), "no topic");

        OutboxEventEntity row = repository.findById(appended.eventId()).orElseThrow();
        assertThat(row.isDeadLettered()).isTrue();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getLastError()).isEqualTo("no topic");
    }

    @Test
    void markPublishedRemovesFromPending() {
        OutboxEventRecord appended = append(NOW.minusSeconds(10));
        store.claimPending(10, NOW, LEASE);

        store.markPublished(appended.eventId(), NOW);

        OutboxEventEntity row = repository.findById(appended.eventId()).orElseThrow();
        assertThat(row.getPublishedAt()).isEqualTo(NOW);
        assertThat(row.getClaimedUntil()).isNull();
        assertThat(store.claimPending(10, NOW.plus(Duration.ofDays(1)), LEASE)).isEmpty();
    }

    @Test
    void oldestPendingAgeIgnoresPublishedAndDeadLettered() {
        OutboxEventRecord published = append(NOW.minusSeconds(500));
        OutboxEventRecord deadLettered = append(NOW.minusSeconds(400));
        append(NOW.minusSeconds(60));
        store.markPublished(published.eventId(), NOW);
        store.markDeadLettered(deadLettered.eventId(), "no topic");

        assertThat(store.oldestPendingAge(NOW)).contains(Duration.ofSeconds(60));
    }

    @Test
    void oldestPendingAgeIsEmptyWhenNothingIsPending() {
        assertThat(store.oldestPendingAge(NOW)).isEmpty();
    }

    @Test
    void deletePublishedBeforeKeepsPendingAndDeadLettered() {
        OutboxEventRecord published = append(NOW.minusSeconds(500));
        OutboxEventRecord pending = append(NOW.minusSeconds(400));
        OutboxEventRecord deadLettered = append(NOW.minusSeconds(300));
        store.markPublished(published.eventId(), NOW.minusSeconds(200));
        store.markDeadLettered(deadLettered.eventId(), "no topic");

        int deleted = store.deletePublishedBefore(NOW.plus(Duration.ofDays(1)), 100);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll())
                .extracting(OutboxEventEntity::getEventId)
                .containsExactlyInAnyOrder(pending.eventId(), deadLettered.eventId());
    }

    @Test
    void deletePublishedBeforeRespectsLimitAndCutoff() {
        OutboxEventRecord oldest = append(NOW.minusSeconds(500));
        OutboxEventRecord older = append(NOW.minusSeconds(400));
        OutboxEventRecord recent = append(NOW.minusSeconds(300));
        store.markPublished(oldest.eventId(), NOW.minusSeconds(300));
        store.markPublished(older.eventId(), NOW.minusSeconds(200));
        store.markPublished(recent.eventId(), NOW.minusSeconds(10));

        int deleted = store.deletePublishedBefore(NOW.minusSeconds(100), 1);

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findAll()).hasSize(2);
    }

    private OutboxEventRecord append(Instant occurredAt) {
        OutboxEventRecord event = event(occurredAt);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> store.append(event));
        return event;
    }

    private static OutboxEventRecord event(Instant occurredAt) {
        return new OutboxEventRecord(
                UUID.randomUUID(), "customer-1", "KycVerificationRequested", "{\"k\":\"v\"}", occurredAt, 0);
    }
}
