package dev.nerviz.bankapp.infrastructure.scheduling.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.usecase.outbox.PruneOutboxEventsUseCase;
import dev.nerviz.bankapp.application.usecase.outbox.RelayOutboxEventsUseCase;
import dev.nerviz.bankapp.infrastructure.scheduling.JobRunRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Binding, startup validation and the on/off gates of both jobs, in a minimal context. The
 * jobs are real here — this is the only place their wiring (constructor arguments,
 * {@code @ConditionalOnProperty}, the properties bean) is exercised with the gate on.
 */
class OutboxPropertiesTest {

    private static final Map<String, String> VALID = Map.of(
            "app.outbox.batch-size", "10",
            "app.outbox.max-attempts", "10",
            "app.outbox.backoff-base", "PT1S",
            "app.outbox.backoff-max", "PT5M",
            "app.outbox.lease", "PT8M",
            "app.outbox.prune-after", "P7D",
            "app.outbox.prune-batch-size", "500");

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class);

    @Test
    void bindsConfiguredValues() {
        contextRunner
                .withPropertyValues(properties(Map.of()))
                .run(context -> assertThat(context)
                        .getBean(OutboxProperties.class)
                        .isEqualTo(new OutboxProperties(
                                10,
                                10,
                                Duration.ofSeconds(1),
                                Duration.ofMinutes(5),
                                Duration.ofMinutes(8),
                                Duration.ofDays(7),
                                500)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"app.outbox.batch-size", "app.outbox.max-attempts", "app.outbox.prune-batch-size"})
    void rejectsCountBelowOne(String key) {
        contextRunner
                .withPropertyValues(properties(Map.of(key, "0")))
                .run(OutboxPropertiesTest::assertContextFailedToStart);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "app.outbox.batch-size",
                "app.outbox.max-attempts",
                "app.outbox.backoff-base",
                "app.outbox.backoff-max",
                "app.outbox.lease",
                "app.outbox.prune-after",
                "app.outbox.prune-batch-size"
            })
    void failsTheBootWhenAKeyIsMissing(String key) {
        contextRunner.withPropertyValues(propertiesWithout(key)).run(OutboxPropertiesTest::assertContextFailedToStart);
    }

    @Test
    void startsBothJobsWhenTheirGatesAreOn() {
        new ApplicationContextRunner()
                .withUserConfiguration(JobsConfig.class)
                .withPropertyValues(
                        properties(Map.of("app.outbox.enabled", "true", "app.outbox.prune-enabled", "true")))
                .run(context ->
                        assertThat(context).hasSingleBean(OutboxRelayJob.class).hasSingleBean(OutboxPruneJob.class));
    }

    @Test
    void startsNeitherJobWhenTheirGatesAreOff() {
        new ApplicationContextRunner()
                .withUserConfiguration(JobsConfig.class)
                .withPropertyValues(
                        properties(Map.of("app.outbox.enabled", "false", "app.outbox.prune-enabled", "false")))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(OutboxRelayJob.class)
                        .doesNotHaveBean(OutboxPruneJob.class));
    }

    @Test
    void gatesTheTwoJobsIndependently() {
        new ApplicationContextRunner()
                .withUserConfiguration(JobsConfig.class)
                .withPropertyValues(
                        properties(Map.of("app.outbox.enabled", "true", "app.outbox.prune-enabled", "false")))
                .run(context ->
                        assertThat(context).hasSingleBean(OutboxRelayJob.class).doesNotHaveBean(OutboxPruneJob.class));
    }

    private static String[] properties(Map<String, String> overrides) {
        Map<String, String> merged = new HashMap<>(VALID);
        merged.putAll(overrides);
        return toArray(merged);
    }

    private static String[] propertiesWithout(String key) {
        Map<String, String> merged = new HashMap<>(VALID);
        merged.remove(key);
        return toArray(merged);
    }

    private static String[] toArray(Map<String, String> values) {
        List<String> lines = values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        return lines.toArray(String[]::new);
    }

    private static void assertContextFailedToStart(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
    }

    @Configuration
    @EnableConfigurationProperties(OutboxProperties.class)
    static class PropertiesConfig {}

    @Configuration
    @Import({OutboxRelayJob.class, OutboxPruneJob.class})
    static class JobsConfig {

        @Bean
        RelayOutboxEventsUseCase relayOutboxEvents() {
            return Mockito.mock(RelayOutboxEventsUseCase.class);
        }

        @Bean
        PruneOutboxEventsUseCase pruneOutboxEvents() {
            return Mockito.mock(PruneOutboxEventsUseCase.class);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        Clock clock() {
            return CustomerFixtures.FIXED_CLOCK;
        }

        @Bean
        JobRunRecorder jobRunRecorder(MeterRegistry meters, Clock clock) {
            return new JobRunRecorder(meters, clock);
        }
    }
}
