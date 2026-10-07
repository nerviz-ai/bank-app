package dev.nerviz.bankapp;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class BankAppApplicationTests {

    @Test
    void contextLoads() {}

    // app.jobs.prune-idempotency-keys.enabled: false in application-test.yml must actually
    // keep the trigger's @ConditionalOnProperty from registering it — proven here, in the
    // context that boots every trigger (.claude/rules/scheduling.md § Triggers). The job stays
    // package-private, so the check goes by bean name rather than importing its type.
    @Test
    void pruneJobIsOffUnderTestProfile(@Autowired ApplicationContext context) {
        assertThat(context.containsBean("pruneExpiredIdempotencyKeysJob"))
                .as("app.jobs.prune-idempotency-keys.enabled=false must keep the trigger out of the context")
                .isFalse();
    }

    // features.observability is on, but nothing registers a Tracer bean on its own — the
    // bridge and the OTLP exporter alone don't; the Boot glue starter does. A missing
    // starter otherwise fails far from the cause, at the first class that injects Tracer.
    // See .claude/rules/observability.md § Correlation.
    @Test
    void registersTracerBean(@Autowired ApplicationContext context) {
        assertThat(context.getBeanProvider(Tracer.class).getIfAvailable())
                .as("features.observability is on, but nothing registers a Tracer bean")
                .isNotNull();
    }
}
