package dev.nerviz.bankapp.infrastructure.messaging.kycverificationcompleted;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.kafka.topics.kyc-verification-completed.*}. No retention property: the payload
 * carries no personal data (25-messaging.md § 8), unlike the request topic's.
 */
@ConfigurationProperties(prefix = "app.kafka.topics.kyc-verification-completed")
@Validated
record KycVerificationCompletedTopicProperties(
        @Min(1) int partitions, @Min(1) short replicas) {}
