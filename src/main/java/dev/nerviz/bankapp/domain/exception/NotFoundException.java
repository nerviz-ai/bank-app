package dev.nerviz.bankapp.domain.exception;

/**
 * Referenced entity doesn't exist — {@.claude/rules/error-handling.md}.
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }
}
