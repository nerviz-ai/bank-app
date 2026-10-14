package dev.nerviz.bankapp.infrastructure.messaging.listener;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Blocking retry and the DLQ for every {@code @KafkaListener} of the project
 * ({@code .claude/rules/messaging.md} § Retry and DLQ): 3 retries, 1 s / 2 s / 4 s, then
 * {@link MonitoredDeadLetterPublisher}; which failure retries is
 * {@link ListenerFailureClassification}'s. Boot hands the single {@code CommonErrorHandler} bean
 * to its listener container factory.
 */
@Configuration
class KafkaListenerErrorHandling {

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_INTERVAL_MS = 1_000L;
    private static final double MULTIPLIER = 2.0;
    private static final long MAX_INTERVAL_MS = 4_000L;

    @Bean
    DefaultErrorHandler kafkaListenerErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        MonitoredDeadLetterPublisher deadLetters = new MonitoredDeadLetterPublisher(
                outRecord -> kafkaTemplate, MonitoredDeadLetterPublisher::sameTopicDlq, meterRegistry);
        deadLetters.addHeadersFunction(MonitoredDeadLetterPublisher::errorCodeHeader);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(deadLetters, backOff());
        ListenerFailureClassification.applyTo(errorHandler);
        return errorHandler;
    }

    private static ExponentialBackOffWithMaxRetries backOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        return backOff;
    }
}
