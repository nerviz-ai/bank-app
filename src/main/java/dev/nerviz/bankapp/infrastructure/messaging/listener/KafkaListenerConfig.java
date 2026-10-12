package dev.nerviz.bankapp.infrastructure.messaging.listener;

import dev.nerviz.bankapp.domain.exception.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared listener wiring for every {@code @KafkaListener} in the project — one converter, one
 * error handler, package-private per {@code .claude/rules/messaging.md} § Boundary ("Framework
 * wiring for the broker lives in this package too").
 *
 * <p>Retry: 3 attempts, 1 s / 2 s / 4 s, then the {@link DeadLetterPublishingRecoverer}.
 * {@link DomainException} is not retryable — a business rejection or a malformed message isn't
 * fixed by waiting. The one named exception, {@code ConflictException}'s
 * {@code CUSTOMER_CONCURRENT_UPDATE}, can't be classified here: Spring Kafka's exception
 * classifier matches by exact Java type, and {@code error-handling.md} forbids a second
 * {@code RuntimeException} root to carry a different classification for the same type. That one
 * errorCode retries in-process, inside the listener that already knows it
 * (25-mensageria.md § 4, {@code KycVerificationCompletedListener}). Conversion failures
 * (malformed JSON) are already in {@link DefaultErrorHandler}'s own fatal-exception default —
 * nothing to add here for them.
 */
@Configuration
class KafkaListenerConfig {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 4000L;
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final String DLQ_SUFFIX = ".dlq";

    @Bean
    StringJacksonJsonMessageConverter kafkaRecordMessageConverter(JsonMapper jsonMapper) {
        return new StringJacksonJsonMessageConverter(jsonMapper);
    }

    @Bean
    DefaultErrorHandler kafkaListenerErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry, JsonMapper jsonMapper) {
        DeadLetterPublishingRecoverer deadLetterRecoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (consumerRecord, exception) ->
                        new TopicPartition(consumerRecord.topic() + DLQ_SUFFIX, consumerRecord.partition()));
        ConsumerRecordRecoverer monitoredRecoverer =
                new DeadLetterMonitoringRecoverer(deadLetterRecoverer, meterRegistry, jsonMapper);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(monitoredRecoverer, backOff());
        errorHandler.addNotRetryableExceptions(DomainException.class);
        return errorHandler;
    }

    private static ExponentialBackOffWithMaxRetries backOff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRY_ATTEMPTS);
        backOff.setInitialInterval(INITIAL_BACKOFF_MS);
        backOff.setMultiplier(BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(MAX_BACKOFF_MS);
        return backOff;
    }
}
