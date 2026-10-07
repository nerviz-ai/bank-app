package dev.nerviz.bankapp.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Transport-agnostic: the adapter computes {@code route}, {@code callerIdentity} and
 * {@code bodyHash} from the request; the application never sees HTTP types.
 */
public record IdempotencyRequest(UUID key, String route, String callerIdentity, String bodyHash, Instant now) {}
