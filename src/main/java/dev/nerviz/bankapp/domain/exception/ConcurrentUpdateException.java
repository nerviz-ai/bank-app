package dev.nerviz.bankapp.domain.exception;

/**
 * Concurrent modification — another writer changed the aggregate between this one's read and its
 * write. The one named subclass {@code .claude/rules/error-handling.md} § Shape of the base
 * classes fixes up front: a message listener's retry classifies by type
 * ({@code .claude/rules/messaging.md} § Retry and DLQ), so a lost race must be its own type to
 * retry while every other {@link ConflictException} dead-letters. Thrown by the persistence
 * adapter, wrapping the framework exception it translates — never by the domain.
 */
public class ConcurrentUpdateException extends ConflictException {

    public ConcurrentUpdateException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
