package dev.nerviz.bankapp.infrastructure.scheduling.outbox;

import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import dev.nerviz.bankapp.application.port.RelayOutcome;
import dev.nerviz.bankapp.application.usecase.outbox.RelayOutboxEventsCommand;
import dev.nerviz.bankapp.application.usecase.outbox.RelayOutboxEventsUseCase;
import dev.nerviz.bankapp.infrastructure.scheduling.JobRunRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trigger only — "the clock fired" becomes one call on {@link RelayOutboxEventsUseCase}. No
 * lock around it: every replica runs the pass and the leased claim hands each a different set
 * of rows (35-jobs.md § 3). Fixed delay, so a slow pass never overlaps the next on one instance.
 *
 * <p>Records the two delivery metrics a "must not be lost" event requires
 * ({@code .claude/rules/observability.md} § Metrics): the dead-letter counter, from each
 * pass's outcome, and the age of the oldest pending row. A give-up path that only logs would
 * make the loss silent.
 */
@Component
@ConditionalOnProperty(name = "app.outbox.enabled", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxRelayJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayJob.class);

    private final RelayOutboxEventsUseCase relayOutboxEvents;
    private final RelayOutboxEventsCommand command;
    private final JobRunRecorder recorder;
    private final Counter deadLettered;

    OutboxRelayJob(
            RelayOutboxEventsUseCase relayOutboxEvents,
            OutboxProperties properties,
            JobRunRecorder recorder,
            MeterRegistry meters) {
        this.relayOutboxEvents = relayOutboxEvents;
        this.command = commandFrom(properties);
        this.recorder = recorder;
        this.deadLettered = Counter.builder("outbox.events.dead_lettered")
                .description("Outbox rows abandoned after exhausting their attempts or found unroutable")
                .register(meters);
        Gauge.builder("outbox.pending.age.seconds", relayOutboxEvents, OutboxRelayJob::pendingAgeSeconds)
                .description("Age of the oldest still-pending outbox row")
                .register(meters);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT1S}")
    void run() {
        recorder.run("outbox-relay", this::relay);
    }

    private void relay() {
        RelayOutcome outcome = relayOutboxEvents.relayPending(command);
        deadLettered.increment(outcome.deadLettered());
        if (outcome.published() + outcome.failed() + outcome.deadLettered() > 0) {
            log.info(
                    "Outbox relay pass: published={} failed={} deadLettered={}",
                    outcome.published(),
                    outcome.failed(),
                    outcome.deadLettered());
        } else {
            log.debug("Outbox relay pass found nothing due");
        }
    }

    private static RelayOutboxEventsCommand commandFrom(OutboxProperties properties) {
        return new RelayOutboxEventsCommand(
                properties.batchSize(),
                properties.lease(),
                new OutboxRetryPolicy(properties.maxAttempts(), properties.backoffBase(), properties.backoffMax()));
    }

    private static double pendingAgeSeconds(RelayOutboxEventsUseCase relayOutboxEvents) {
        return relayOutboxEvents.oldestPendingAge().map(Duration::toSeconds).orElse(0L);
    }
}
