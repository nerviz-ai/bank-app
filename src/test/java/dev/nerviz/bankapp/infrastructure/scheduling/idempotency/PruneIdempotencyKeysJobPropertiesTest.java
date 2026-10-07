package dev.nerviz.bankapp.infrastructure.scheduling.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Binding and validation only — {@code @ConfigurationProperties} +
 * {@code @Validated} fail context startup on an out-of-range value
 * ({@.claude/rules/scheduling.md}).
 */
class PruneIdempotencyKeysJobPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsConfiguredValues() {
        contextRunner
                .withPropertyValues(
                        "app.jobs.prune-idempotency-keys.batch-size=1000",
                        "app.jobs.prune-idempotency-keys.max-batches=100")
                .run(context -> assertThat(context)
                        .getBean(PruneIdempotencyKeysJobProperties.class)
                        .satisfies(properties -> {
                            assertThat(properties.batchSize()).isEqualTo(1000);
                            assertThat(properties.maxBatches()).isEqualTo(100);
                        }));
    }

    @Test
    void rejectsBatchSizeBelowOne() {
        contextRunner
                .withPropertyValues(
                        "app.jobs.prune-idempotency-keys.batch-size=0",
                        "app.jobs.prune-idempotency-keys.max-batches=100")
                .run(PruneIdempotencyKeysJobPropertiesTest::assertContextFailedToStart);
    }

    @Test
    void rejectsMaxBatchesBelowOne() {
        contextRunner
                .withPropertyValues(
                        "app.jobs.prune-idempotency-keys.batch-size=1000",
                        "app.jobs.prune-idempotency-keys.max-batches=0")
                .run(PruneIdempotencyKeysJobPropertiesTest::assertContextFailedToStart);
    }

    private static void assertContextFailedToStart(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
    }

    @Configuration
    @EnableConfigurationProperties(PruneIdempotencyKeysJobProperties.class)
    static class PropertiesConfig {}
}
