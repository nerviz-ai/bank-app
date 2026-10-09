package dev.nerviz.bankapp.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * One failed send of one row, as the relay reports it to the gateway. {@code reason} is the
 * broker error text — never the payload.
 */
public record OutboxFailure(UUID eventId, String reason, Instant failedAt) {}
