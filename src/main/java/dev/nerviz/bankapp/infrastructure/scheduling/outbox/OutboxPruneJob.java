package dev.nerviz.bankapp.infrastructure.scheduling.outbox;

import dev.nerviz.bankapp.application.usecase.outbox.PruneOutboxEventsCommand;
import dev.nerviz.bankapp.application.usecase.outbox.PruneOutboxEventsUseCase;
import dev.nerviz.bankapp.infrastructure.scheduling.JobRunRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trigger only — calendar-anchored, off-peak, explicit zone. Every replica fires at the same
 * time; the bounded delete skips rows a concurrent transaction holds, so they split the rows
 * and no lock is needed (35-jobs.md § 3). A missed run is skipped: the cutoff is
 * {@code now − prune-after} against persisted state, so the next run catches up. This job is
 * what makes the retention of a payload carrying personal data real
 * ({@code .claude/rules/scheduling.md} § Retention).
 */
@Component
@ConditionalOnProperty(name = "app.outbox.prune-enabled", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxPruneJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxPruneJob.class);

    private final PruneOutboxEventsUseCase pruneOutboxEvents;
    private final PruneOutboxEventsCommand command;
    private final JobRunRecorder recorder;

    OutboxPruneJob(PruneOutboxEventsUseCase pruneOutboxEvents, OutboxProperties properties, JobRunRecorder recorder) {
        this.pruneOutboxEvents = pruneOutboxEvents;
        this.command = new PruneOutboxEventsCommand(properties.pruneAfter(), properties.pruneBatchSize());
        this.recorder = recorder;
    }

    @Scheduled(cron = "${app.outbox.prune-cron:0 30 3 * * *}", zone = "UTC")
    void run() {
        recorder.run("outbox-prune", this::prune);
    }

    private void prune() {
        long deleted = pruneOutboxEvents.prunePublished(command);
        if (deleted > 0) {
            log.info("Pruned {} published outbox event(s)", deleted);
        } else {
            log.debug("No published outbox events to prune");
        }
    }
}
