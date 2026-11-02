package dev.nerviz.bankapp;

import dev.nerviz.bankapp.domain.model.Account;
import dev.nerviz.bankapp.domain.model.AccountId;
import dev.nerviz.bankapp.domain.model.Customer;
import java.util.UUID;

/** Accounts reached through {@code Account.open}, never rebuilt with {@code rehydrate}. */
public final class AccountFixtures {

    private AccountFixtures() {}

    public static Account openFor(Customer customer) {
        return Account.open(AccountId.of(UUID.randomUUID()), customer);
    }
}
