package dev.nerviz.bankapp.application.shared;

import dev.nerviz.bankapp.application.port.StoredResponse;

/**
 * Executed: the effect ran now, the adapter builds a fresh response from the result.
 * Replayed: a prior request already finished, the adapter returns its response verbatim.
 */
public sealed interface IdempotentOutcome<R> {
    record Executed<R>(R result) implements IdempotentOutcome<R> {}

    record Replayed<R>(StoredResponse response) implements IdempotentOutcome<R> {}
}
