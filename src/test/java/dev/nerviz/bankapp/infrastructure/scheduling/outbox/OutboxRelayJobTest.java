package dev.nerviz.bankapp.infrastructure.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import dev.nerviz.bankapp.application.port.RelayOutcome;
import dev.nerviz.bankapp.application.usecase.outbox.RelayOutboxEventsCommand;
import dev.nerviz.bankapp.application.usecase.outbox.RelayOutboxEventsUseCase;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.infrastructure.scheduling.JobRunRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Driving adapter with doubles — the pass is called directly, never by waiting for the scheduler
 * ({@.claude/rules/scheduling.md} § Triggers). Meters are read from a real registry.
 */
class OutboxRelayJobTest {

    private static final OutboxProperties PROPERTIES = new OutboxProperties(
            10, 7, Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofMinutes(8), Duration.ofDays(7), 500);

    private final RelayOutboxEventsUseCase relayOutboxEvents = mock(RelayOutboxEventsUseCase.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final JobRunRecorder recorder = new JobRunRecorder(meters, CustomerFixtures.FIXED_CLOCK);

    @Test
    void runsPassWithBatchSizeFromProperties() {
        given(relayOutboxEvents.relayPending(any())).willReturn(RelayOutcome.none());
        OutboxRelayJob job = new OutboxRelayJob(relayOutboxEvents, PROPERTIES, recorder, meters);

        job.run();

        verify(relayOutboxEvents)
                .relayPending(new RelayOutboxEventsCommand(
                        10,
                        Duration.ofMinutes(8),
                        new OutboxRetryPolicy(7, Duration.ofSeconds(1), Duration.ofMinutes(5))));
    }

    @Test
    void incrementsDeadLetteredCounterFromOutcome() {
        given(relayOutboxEvents.relayPending(any())).willReturn(new RelayOutcome(3, 1, 2));
        OutboxRelayJob job = new OutboxRelayJob(relayOutboxEvents, PROPERTIES, recorder, meters);

        job.run();
        job.run();

        assertThat(meters.get("outbox.events.dead_lettered").counter().count()).isEqualTo(4d);
    }

    @Test
    void registersPendingAgeGaugeReadingTheOldestPendingRow() {
        given(relayOutboxEvents.oldestPendingAge()).willReturn(Optional.of(Duration.ofSeconds(90)));
        new OutboxRelayJob(relayOutboxEvents, PROPERTIES, recorder, meters);

        assertThat(meters.get("outbox.pending.age.seconds").gauge().value()).isEqualTo(90d);
    }

    @Test
    void reportsZeroPendingAgeWhenNothingIsPending() {
        given(relayOutboxEvents.oldestPendingAge()).willReturn(Optional.empty());
        new OutboxRelayJob(relayOutboxEvents, PROPERTIES, recorder, meters);

        assertThat(meters.get("outbox.pending.age.seconds").gauge().value()).isZero();
    }

    @Test
    void recordsTheRunUnderItsOwnJobName() {
        given(relayOutboxEvents.relayPending(any())).willReturn(RelayOutcome.publishedOne());
        OutboxRelayJob job = new OutboxRelayJob(relayOutboxEvents, PROPERTIES, recorder, meters);

        job.run();

        assertThat(meters.get("jobs.execution")
                        .tag("job", "outbox-relay")
                        .tag("outcome", "success")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveLeaseWhenBuilt() {
        OutboxProperties noLease = new OutboxProperties(
                10, 7, Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ZERO, Duration.ofDays(7), 500);

        assertThatThrownBy(() -> new OutboxRelayJob(relayOutboxEvents, noLease, recorder, meters))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("OUTBOX_LEASE_INVALID");
    }
}
