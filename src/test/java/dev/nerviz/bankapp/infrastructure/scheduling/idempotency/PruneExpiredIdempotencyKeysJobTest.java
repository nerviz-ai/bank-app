package dev.nerviz.bankapp.infrastructure.scheduling.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.application.usecase.idempotency.PruneExpiredIdempotencyKeysCommand;
import dev.nerviz.bankapp.application.usecase.idempotency.PruneExpiredIdempotencyKeysUseCase;
import dev.nerviz.bankapp.infrastructure.scheduling.JobRunRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Driving adapter with doubles — never waiting on the scheduler
 * ({@.claude/rules/scheduling.md} § Triggers). The real {@link JobRunRecorder} over a
 * {@link SimpleMeterRegistry} proves the job is recorded under its own name, not a double
 * of the recorder itself.
 */
class PruneExpiredIdempotencyKeysJobTest {

    private final PruneExpiredIdempotencyKeysUseCase pruneExpiredIdempotencyKeys =
            mock(PruneExpiredIdempotencyKeysUseCase.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private final JobRunRecorder recorder = new JobRunRecorder(meterRegistry, clock);

    @Test
    void callsUseCaseWithCommandFromProperties() {
        PruneIdempotencyKeysJobProperties properties = new PruneIdempotencyKeysJobProperties(1000, 100);
        PruneExpiredIdempotencyKeysJob job =
                new PruneExpiredIdempotencyKeysJob(pruneExpiredIdempotencyKeys, properties, recorder);

        job.run();

        verify(pruneExpiredIdempotencyKeys).prune(eq(new PruneExpiredIdempotencyKeysCommand(1000, 100)));
    }

    @Test
    void recordsRunUnderJobName() {
        PruneIdempotencyKeysJobProperties properties = new PruneIdempotencyKeysJobProperties(1000, 100);
        given(pruneExpiredIdempotencyKeys.prune(eq(new PruneExpiredIdempotencyKeysCommand(1000, 100))))
                .willReturn(3L);
        PruneExpiredIdempotencyKeysJob job =
                new PruneExpiredIdempotencyKeysJob(pruneExpiredIdempotencyKeys, properties, recorder);

        job.run();

        assertThat(meterRegistry
                        .get("jobs.execution")
                        .tag("job", "prune-idempotency-keys")
                        .tag("outcome", "success")
                        .timer()
                        .count())
                .isEqualTo(1);
    }
}
