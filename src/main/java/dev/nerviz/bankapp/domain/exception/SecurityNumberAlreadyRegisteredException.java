package dev.nerviz.bankapp.domain.exception;

/**
 * Named subclass of {@link ConflictException}: the same {@code errorCode} is raised at two
 * call sites in UC-001 — the use case's pre-check and the persistence adapter's translation
 * of the unique-constraint violation on {@code security_number}.
 */
public class SecurityNumberAlreadyRegisteredException extends ConflictException {

    private static final String ERROR_CODE = "SECURITY_NUMBER_ALREADY_REGISTERED";

    public SecurityNumberAlreadyRegisteredException(String message) {
        super(ERROR_CODE, message);
    }

    public SecurityNumberAlreadyRegisteredException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
