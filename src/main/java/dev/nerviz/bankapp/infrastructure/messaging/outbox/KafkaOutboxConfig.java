package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Adapter-local wiring: it builds broker types, so it sits with the adapter and not in the
 * project-wide configuration package ({@code .claude/rules/messaging.md} § Boundary). The
 * producer itself is Boot's, configured by {@code spring.kafka.*}.
 */
@Configuration
@EnableConfigurationProperties(KycVerificationRequestedTopicProperties.class)
class KafkaOutboxConfig {

    @Bean
    NewTopic kycVerificationRequestedTopic(KycVerificationRequestedTopicProperties properties) {
        return TopicBuilder.name(TopicResolver.KYC_VERIFICATION_REQUESTED_TOPIC)
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .config(
                        TopicConfig.RETENTION_MS_CONFIG,
                        String.valueOf(properties.retention().toMillis()))
                .build();
    }
}
