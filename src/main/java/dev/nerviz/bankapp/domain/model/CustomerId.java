package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.util.UUID;

/** Prevents passing another aggregate's id in a {@code Customer}'s place. */
public record CustomerId(UUID value) {

    public CustomerId {
        if (value == null) {
            throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
        }
    }

    public static CustomerId of(UUID value) {
        return new CustomerId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
