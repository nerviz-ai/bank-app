package dev.nerviz.bankapp.infrastructure.scheduling.outbox;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code app.outbox.*} values both jobs read, bound once and validated at startup. Deliberately
 * not fields: {@code enabled}, {@code poll-interval}, {@code prune-enabled} and
 * {@code prune-cron} are read by the placeholders and {@code @ConditionalOnProperty} of the
 * jobs themselves, and a second binding here would be a copy read by nothing
 * ({@code .claude/rules/scheduling.md} § Triggers). The values are the persistence design's
 * (20-persistencia.md § 1 and § 5); none has a default here, so a missing one fails the boot
 * instead of running on a number nobody chose.
 */
@ConfigurationProperties(prefix = "app.outbox")
@Validated
record OutboxProperties(
        @Min(1) int batchSize,
        @Min(1) int maxAttempts,
        @NotNull Duration backoffBase,
        @NotNull Duration backoffMax,
        @NotNull Duration lease,
        @NotNull Duration pruneAfter,
        @Min(1) int pruneBatchSize) {}
