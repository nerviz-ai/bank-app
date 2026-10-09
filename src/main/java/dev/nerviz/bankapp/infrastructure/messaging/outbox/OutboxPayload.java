package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import java.time.Instant;

/**
 * What {@link OutboxAppender} needs to know about any flow's payload record, and nothing more:
 * the identity the row is stored under and the instant it occurred. The payload class itself
 * stays package-private in its flow's subpackage; this is the one way across.
 */
public interface OutboxPayload {

    String eventId();

    Instant occurredAt();
}
