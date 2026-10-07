package dev.nerviz.bankapp.domain.exception;

/**
 * Well-formed input that violates a business rule — {@.claude/rules/error-handling.md}.
 */
public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessRuleViolationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
