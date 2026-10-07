package dev.nerviz.bankapp.domain.exception;

/**
 * Duplicate or concurrent modification — {@.claude/rules/error-handling.md}.
 */
public class ConflictException extends DomainException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConflictException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
