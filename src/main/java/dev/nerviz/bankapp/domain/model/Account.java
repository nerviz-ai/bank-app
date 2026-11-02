package dev.nerviz.bankapp.domain.model;

import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.ValidationException;

/**
 * Aggregate root. Boundary: the account alone — the owner is referenced by id, never by the
 * {@link Customer} (separate aggregate, separate lock). {@code open} is the only creation path;
 * {@code rehydrate} is for persistence and is never a transition.
 */
public record Account(AccountId id, CustomerId owner, Money balance, AccountStatus status) {

    public Account {
        if (id == null) {
            throw new ValidationException("ACCOUNT_ID_REQUIRED", "account id is required");
        }
        if (owner == null) {
            throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
        }
        if (balance == null) {
            throw new ValidationException("MONEY_REQUIRED", "amount is required");
        }
        if (status == null) {
            throw new ValidationException("ACCOUNT_STATUS_REQUIRED", "account status is required");
        }
    }

    /**
     * Creation. Starts at {@link Money#zero()}, {@link AccountStatus#ACTIVE}: no parameter lets the
     * caller choose either.
     *
     * @throws BusinessRuleViolationException {@code CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT} — the
     *     customer is not {@code ACTIVE}, or is anonymized
     */
    public static Account open(AccountId id, Customer owner) {
        if (owner == null) {
            throw new ValidationException("CUSTOMER_REQUIRED", "customer is required");
        }
        if (!owner.canHoldAccount()) {
            throw new BusinessRuleViolationException(
                    "CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT", "customer is not eligible to hold an account");
        }
        return new Account(id, owner.id(), Money.zero(), AccountStatus.ACTIVE);
    }

    /** Reconstruction from already-persisted data — used by the persistence adapter. */
    public static Account rehydrate(AccountId id, CustomerId owner, Money balance, AccountStatus status) {
        return new Account(id, owner, balance, status);
    }
}
