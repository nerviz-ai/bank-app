package dev.nerviz.bankapp.infrastructure.scheduling;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Shared by every job, project-wide — {@code public} because each job's trigger lives in its
 * own subpackage of {@code infrastructure.scheduling} (one per aggregate/job,
 * {@.claude/rules/naming.md}), a different package from this one.
 *
 * <p>Gives every job the two metrics {@.claude/rules/scheduling.md} § Observability requires: a
 * timer by outcome and the time of the last success. A job that stopped raises no error;
 * staleness is the only signal, and only if something measures it.
 */
@Component
public class JobRunRecorder {

    private static final double MILLIS_PER_SECOND = 1000d;

    private final MeterRegistry meters;
    private final Clock clock;
    private final Map<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public JobRunRecorder(MeterRegistry meters, Clock clock) {
        this.meters = meters;
        this.clock = clock;
    }

    /**
     * Runs one pass and records it. A failure still escapes to the scheduler's error handler,
     * which logs it — no catch of the base runtime exception here. What this adds is what the
     * log line never gives: a rate to alarm on, and the time of the last success. The tag set is
     * closed — job name and outcome — so the cardinality is the job count times two
     * ({@.claude/rules/observability.md} § Metrics).
     */
    public void run(String job, Runnable pass) {
        AtomicLong last = lastSuccessOf(job);
        Timer.Sample sample = Timer.start(meters);
        boolean succeeded = false;
        try {
            pass.run();
            succeeded = true;
            last.set(clock.millis());
        } finally {
            String outcome = succeeded ? "success" : "failure";
            sample.stop(meters.timer("jobs.execution", "job", job, "outcome", outcome));
        }
    }

    /**
     * Registered on the first pass and seeded with the start time, so a job that has never
     * succeeded ages from boot instead of reading as "succeeded in 1970".
     */
    private AtomicLong lastSuccessOf(String job) {
        return lastSuccess.computeIfAbsent(job, name -> {
            AtomicLong at = new AtomicLong(clock.millis());
            Gauge.builder("jobs.last_success.seconds", at, value -> value.get() / MILLIS_PER_SECOND)
                    .tag("job", name)
                    .description("Epoch second of the job's last successful pass")
                    .register(meters);
            return at;
        });
    }
}
