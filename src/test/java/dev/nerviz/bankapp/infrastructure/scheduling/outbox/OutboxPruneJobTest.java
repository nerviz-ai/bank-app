package dev.nerviz.bankapp.infrastructure.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.usecase.outbox.PruneOutboxEventsCommand;
import dev.nerviz.bankapp.application.usecase.outbox.PruneOutboxEventsUseCase;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.infrastructure.scheduling.JobRunRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Driving adapter with doubles — the pass is called directly, never by waiting for the clock. */
class OutboxPruneJobTest {

    private static final OutboxProperties PROPERTIES = new OutboxProperties(
            10, 7, Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofMinutes(8), Duration.ofDays(7), 500);

    private final PruneOutboxEventsUseCase pruneOutboxEvents = mock(PruneOutboxEventsUseCase.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final JobRunRecorder recorder = new JobRunRecorder(meters, CustomerFixtures.FIXED_CLOCK);

    @Test
    void prunesWithRetentionAndBatchFromProperties() {
        OutboxPruneJob job = new OutboxPruneJob(pruneOutboxEvents, PROPERTIES, recorder);

        job.run();

        verify(pruneOutboxEvents).prunePublished(new PruneOutboxEventsCommand(Duration.ofDays(7), 500));
    }

    @Test
    void recordsTheRunUnderItsOwnJobName() {
        given(pruneOutboxEvents.prunePublished(any())).willReturn(3L);
        OutboxPruneJob job = new OutboxPruneJob(pruneOutboxEvents, PROPERTIES, recorder);

        job.run();

        assertThat(meters.get("jobs.execution")
                        .tag("job", "outbox-prune")
                        .tag("outcome", "success")
                        .timer()
                        .count())
                .isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveRetentionWhenBuilt() {
        OutboxProperties noRetention = new OutboxProperties(
                10, 7, Duration.ofSeconds(1), Duration.ofMinutes(5), Duration.ofMinutes(8), Duration.ZERO, 500);

        assertThatThrownBy(() -> new OutboxPruneJob(pruneOutboxEvents, noRetention, recorder))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("OUTBOX_RETENTION_INVALID");
    }
}
