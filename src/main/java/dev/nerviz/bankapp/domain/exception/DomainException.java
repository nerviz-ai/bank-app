package dev.nerviz.bankapp.domain.exception;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

/**
 * Base of the typed family — {@.claude/rules/error-handling.md}. {@code errorCode} is
 * mandatory in every constructor: there's no {@code DomainException} without a stable code.
 */
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class DomainException extends RuntimeException {

    String errorCode;

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
