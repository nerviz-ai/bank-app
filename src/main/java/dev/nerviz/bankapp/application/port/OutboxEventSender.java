package dev.nerviz.bankapp.application.port;

/**
 * Output port, kind messaging — the relay's only view of the broker. Never throws for a send
 * that did not succeed: the failure is an {@link OutboxSendResult}, because one poisoned row
 * must not stall the batch.
 */
public interface OutboxEventSender {

    OutboxSendResult send(OutboxEventRecord event);
}
