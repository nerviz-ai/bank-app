package dev.nerviz.bankapp.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * The one carrier of an outbox row, both directions: the appender writes it ({@code attempts}
 * zero), the relay reads it. {@code payload} is JSON text, carried untouched from append to
 * send. The topic is not a component: no column holds it, the sender resolves it from
 * {@code eventType}. {@code toString()} omits the payload, which can hold personal data.
 */
public record OutboxEventRecord(
        UUID eventId, String aggregateId, String eventType, String payload, Instant occurredAt, int attempts) {

    @Override
    public String toString() {
        return "OutboxEventRecord[eventId=" + eventId + ", aggregateId=" + aggregateId + ", eventType=" + eventType
                + ", occurredAt=" + occurredAt + ", attempts=" + attempts + "]";
    }
}
