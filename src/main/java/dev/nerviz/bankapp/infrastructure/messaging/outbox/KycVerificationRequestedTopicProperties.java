package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.kafka.topics.kyc-verification-requested.*}. Retention is explicit and configurable
 * because the payload carries a national id in clear (25-mensageria.md § 9,
 * {@code .claude/rules/personal-data.md} § In transit).
 */
@ConfigurationProperties(prefix = "app.kafka.topics.kyc-verification-requested")
@Validated
record KycVerificationRequestedTopicProperties(
        @Min(1) int partitions,
        @Min(1) short replicas,
        @NotNull Duration retention) {}
