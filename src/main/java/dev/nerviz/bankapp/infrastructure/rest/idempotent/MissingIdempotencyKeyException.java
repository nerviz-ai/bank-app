package dev.nerviz.bankapp.infrastructure.rest.idempotent;

/**
 * Thrown by {@link IdempotencyKeyInterceptor} when {@code Idempotency-Key} is missing or
 * malformed. Deliberately not a {@code DomainException}: the request never reached the use
 * case, so the response carries {@code violations} and no {@code errorCode}.
 */
public class MissingIdempotencyKeyException extends RuntimeException {
    public MissingIdempotencyKeyException(String message) {
        super(message);
    }
}
