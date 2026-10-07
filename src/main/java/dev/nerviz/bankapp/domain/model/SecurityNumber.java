package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.ValidationException;

/**
 * Exactly 11 digits, no check digit (mother spec, user's decision). {@code of} trims
 * surrounding whitespace only — punctuation is rejected, not stripped. {@code toString()}
 * delegates to {@link #masked()} so a value concatenated into a log line or an exception
 * message never prints in clear.
 */
public record SecurityNumber(String value) {

    private static final int LENGTH = 11;

    public SecurityNumber {
        if (value == null) {
            throw new ValidationException("SECURITY_NUMBER_REQUIRED", "security number is required");
        }
        if (value.length() != LENGTH || !value.chars().allMatch(Character::isDigit)) {
            throw new ValidationException("SECURITY_NUMBER_INVALID", "security number must have exactly 11 digits");
        }
    }

    public static SecurityNumber of(String raw) {
        if (raw == null) {
            throw new ValidationException("SECURITY_NUMBER_REQUIRED", "security number is required");
        }
        return new SecurityNumber(raw.trim());
    }

    public String masked() {
        return "*********" + value.substring(LENGTH - 2);
    }

    @Override
    public String toString() {
        return masked();
    }
}
