package dev.nerviz.bankapp.domain.exception;

/**
 * Base of the typed family — {@.claude/rules/error-handling.md}. {@code errorCode} is
 * mandatory in every constructor: there's no {@code DomainException} without a stable code.
 */
public abstract class DomainException extends RuntimeException {

    private final String errorCode;

    protected DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
