package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.math.BigDecimal;

/**
 * An amount in the project's single currency (USD, implicit — no currency field). Formation rule:
 * required, non-negative, at most two decimals. The stored amount always has scale 2, so
 * {@code 10} and {@code 10.00} are the same value.
 */
public record Money(BigDecimal amount) {

    private static final int SCALE = 2;

    public Money {
        if (amount == null) {
            throw new ValidationException("MONEY_REQUIRED", "amount is required");
        }
        if (amount.signum() < 0) {
            throw new ValidationException("MONEY_NEGATIVE", "amount cannot be negative");
        }
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new ValidationException("MONEY_SCALE_INVALID", "amount cannot have more than 2 decimals");
        }
        amount = amount.setScale(SCALE);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO);
    }
}
