package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.util.UUID;

/** Prevents passing another aggregate's id in an {@code Account}'s place. */
public record AccountId(UUID value) {

    public AccountId {
        if (value == null) {
            throw new ValidationException("ACCOUNT_ID_REQUIRED", "account id is required");
        }
    }

    public static AccountId of(UUID value) {
        return new AccountId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
