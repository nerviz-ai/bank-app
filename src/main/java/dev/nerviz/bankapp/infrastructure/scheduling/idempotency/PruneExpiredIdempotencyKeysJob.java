package dev.nerviz.bankapp.infrastructure.scheduling.idempotency;

import dev.nerviz.bankapp.application.usecase.idempotency.PruneExpiredIdempotencyKeysCommand;
import dev.nerviz.bankapp.application.usecase.idempotency.PruneExpiredIdempotencyKeysUseCase;
import dev.nerviz.bankapp.infrastructure.scheduling.JobRunRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trigger only — translates "the clock fired" into one call on {@link
 * PruneExpiredIdempotencyKeysUseCase}, the single inbound port this job knows. No repository, no
 * JPA type, no business branch ({@.claude/rules/scheduling.md} § Boundary).
 *
 * <p>Every replica runs this pass: {@code deleteExpired}'s {@code FOR UPDATE SKIP LOCKED}
 * partitions the rows between replicas on its own, so no lock around the trigger is needed
 * (UC-002-prune-expired-idempotency-keys spec § 3.6).
 */
@Component
@ConditionalOnProperty(name = "app.jobs.prune-idempotency-keys.enabled", matchIfMissing = true)
@EnableConfigurationProperties(PruneIdempotencyKeysJobProperties.class)
class PruneExpiredIdempotencyKeysJob {

    private static final Logger log = LoggerFactory.getLogger(PruneExpiredIdempotencyKeysJob.class);

    private final PruneExpiredIdempotencyKeysUseCase pruneExpiredIdempotencyKeys;
    private final PruneIdempotencyKeysJobProperties properties;
    private final JobRunRecorder recorder;

    PruneExpiredIdempotencyKeysJob(
            PruneExpiredIdempotencyKeysUseCase pruneExpiredIdempotencyKeys,
            PruneIdempotencyKeysJobProperties properties,
            JobRunRecorder recorder) {
        this.pruneExpiredIdempotencyKeys = pruneExpiredIdempotencyKeys;
        this.properties = properties;
        this.recorder = recorder;
    }

    /**
     * Fixed delay: the next run starts a set interval after the previous one finished, so a slow
     * pass never overlaps the next. Both placeholders carry their own default, read here and
     * nowhere else ({@.claude/rules/scheduling.md} § Triggers).
     */
    @Scheduled(
            fixedDelayString = "${app.jobs.prune-idempotency-keys.interval:PT1H}",
            initialDelayString = "${app.jobs.prune-idempotency-keys.initial-delay:PT5M}")
    void run() {
        recorder.run("prune-idempotency-keys", this::prune);
    }

    private void prune() {
        long deleted = pruneExpiredIdempotencyKeys.prune(
                new PruneExpiredIdempotencyKeysCommand(properties.batchSize(), properties.maxBatches()));
        if (deleted > 0) {
            log.info("Pruned {} expired idempotency key(s)", deleted);
        } else {
            log.debug("No expired idempotency keys to prune");
        }
    }
}
