package dev.nerviz.bankapp.infrastructure.messaging.listener;

import dev.nerviz.bankapp.domain.exception.ConcurrentUpdateException;
import dev.nerviz.bankapp.domain.exception.DomainException;
import org.springframework.kafka.listener.ExceptionClassifier;

/**
 * The one copy of {@code .claude/rules/messaging.md} § Retry and DLQ's table in code. Spring
 * Kafka's classifier takes the nearest entry walking up from the thrown class:
 * {@link ConcurrentUpdateException} retries although {@link DomainException} does not. Conversion
 * failures stay fatal through the classifier's own defaults. No {@code DependencyResponseException}
 * entry: the project has no integration families yet.
 */
final class ListenerFailureClassification {

    private ListenerFailureClassification() {}

    static void applyTo(ExceptionClassifier classifier) {
        classifier.addNotRetryableExceptions(DomainException.class);
        classifier.addRetryableExceptions(ConcurrentUpdateException.class);
    }
}
