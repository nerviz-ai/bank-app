package dev.nerviz.bankapp.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** Fixed clock, no Spring context — {@.claude/rules/scheduling.md} § Observability. */
class JobRunRecorderTest {

    private static final String JOB = "prune-idempotency-keys";

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    void recordsSuccessAndLastSuccessGauge() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
        JobRunRecorder recorder = new JobRunRecorder(meterRegistry, clock);

        recorder.run(JOB, () -> {});

        assertThat(meterRegistry
                        .get("jobs.execution")
                        .tag("job", JOB)
                        .tag("outcome", "success")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(meterRegistry
                        .get("jobs.last_success.seconds")
                        .tag("job", JOB)
                        .gauge()
                        .value())
                .isEqualTo(Instant.parse("2026-01-15T10:00:00Z").toEpochMilli() / 1000d);
    }

    @Test
    void recordsFailureAndRethrows() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
        JobRunRecorder recorder = new JobRunRecorder(meterRegistry, clock);
        recorder.run(JOB, () -> {});
        double gaugeAfterSuccess = meterRegistry
                .get("jobs.last_success.seconds")
                .tag("job", JOB)
                .gauge()
                .value();

        assertThatThrownBy(() -> recorder.run(JOB, JobRunRecorderTest::failingPass))
                .isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry
                        .get("jobs.execution")
                        .tag("job", JOB)
                        .tag("outcome", "failure")
                        .timer()
                        .count())
                .isEqualTo(1);
        assertThat(meterRegistry
                        .get("jobs.last_success.seconds")
                        .tag("job", JOB)
                        .gauge()
                        .value())
                .isEqualTo(gaugeAfterSuccess);
    }

    @Test
    void seedsGaugeWithStartTimeBeforeFirstSuccess() {
        Clock bootTime = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);
        JobRunRecorder recorder = new JobRunRecorder(meterRegistry, bootTime);

        assertThatThrownBy(() -> recorder.run("not-yet-succeeded", JobRunRecorderTest::failingPass))
                .isInstanceOf(IllegalStateException.class);

        assertThat(meterRegistry
                        .get("jobs.last_success.seconds")
                        .tag("job", "not-yet-succeeded")
                        .gauge()
                        .value())
                .isEqualTo(bootTime.instant().toEpochMilli() / 1000d);
    }

    private static void failingPass() {
        throw new IllegalStateException("boom");
    }
}
