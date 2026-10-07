package dev.nerviz.bankapp.domain.exception;

/**
 * Invariant violated while constructing the aggregate or value object —
 * {@.claude/rules/error-handling.md}.
 */
public class ValidationException extends DomainException {

    public ValidationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
