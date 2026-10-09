package dev.nerviz.bankapp.application.port;

/**
 * Three outcomes of sending one row, and the relay treats each differently: a sent row is
 * marked published, a failed one spends an attempt, an unroutable one is dead-lettered now —
 * no retry will map an event type nobody registered. Nothing broker-typed crosses this type.
 */
public sealed interface OutboxSendResult {

    record Sent() implements OutboxSendResult {}

    record Failed(String reason) implements OutboxSendResult {}

    record Unroutable(String reason) implements OutboxSendResult {}
}
