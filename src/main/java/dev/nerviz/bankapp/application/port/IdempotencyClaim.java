package dev.nerviz.bankapp.application.port;

/**
 * Proceed: this request holds the key — run the effect, then {@code complete}.
 * Replay: a prior request with the same key and the same body already finished — return
 * its response verbatim, the effect does not run again.
 */
public sealed interface IdempotencyClaim {
    record Proceed() implements IdempotencyClaim {}

    record Replay(StoredResponse response) implements IdempotencyClaim {}
}
