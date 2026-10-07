package dev.nerviz.bankapp.infrastructure.scheduling;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;

/**
 * The project's only {@code @EnableScheduling} — a second one would mean a second scheduler
 * thread pool for no recorded reason ({@.claude/rules/scheduling.md} § Boundary).
 *
 * <p>Adapter-local wiring stays with its adapter, not in {@code infrastructure.config}: same
 * exception, same reason, as broker wiring in {@.claude/rules/messaging.md} § Boundary.
 */
@Configuration
@EnableScheduling
class SchedulingConfig {

    /**
     * Without this, a scheduled run produces no observation and therefore no trace: the
     * registrar does not pick the registry up on its own.
     */
    @Bean
    SchedulingConfigurer observedScheduling(ObservationRegistry observations) {
        return registrar -> registrar.setObservationRegistry(observations);
    }
}
