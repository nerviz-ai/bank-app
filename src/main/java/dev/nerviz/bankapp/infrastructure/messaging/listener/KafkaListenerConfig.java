package dev.nerviz.bankapp.infrastructure.messaging.listener;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.converter.StringJacksonJsonMessageConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared listener wiring for every {@code @KafkaListener} in the project — the one converter,
 * package-private per {@code .claude/rules/messaging.md} § Boundary ("Framework wiring for the
 * broker lives in this package too"). Retry and the DLQ are {@link KafkaListenerErrorHandling}'s.
 */
@Configuration
class KafkaListenerConfig {

    @Bean
    StringJacksonJsonMessageConverter kafkaRecordMessageConverter(JsonMapper jsonMapper) {
        return new StringJacksonJsonMessageConverter(jsonMapper);
    }
}
