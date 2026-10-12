package dev.nerviz.bankapp.infrastructure.messaging.kycverificationcompleted;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Adapter-local wiring ({@code .claude/rules/messaging.md} § Boundary). Holds the topic-name
 * constant {@link KycVerificationCompletedListener} subscribes to, and declares its DLQ
 * alongside it — the recoverer in {@code listener.KafkaListenerConfig} only computes the name
 * ({@code <topic>.dlq}), it doesn't provision the topic.
 */
@Configuration
@EnableConfigurationProperties(KycVerificationCompletedTopicProperties.class)
class KycVerificationCompletedTopicConfig {

    static final String TOPIC = "bank-app.customer.kyc-verification-completed";
    static final String DLQ_TOPIC = TOPIC + ".dlq";

    @Bean
    NewTopic kycVerificationCompletedTopic(KycVerificationCompletedTopicProperties properties) {
        return TopicBuilder.name(TOPIC)
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .build();
    }

    @Bean
    NewTopic kycVerificationCompletedDlqTopic(KycVerificationCompletedTopicProperties properties) {
        return TopicBuilder.name(DLQ_TOPIC)
                .partitions(properties.partitions())
                .replicas(properties.replicas())
                .build();
    }
}
