package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.application.usecase.account.OpenAccountCommand;
import dev.nerviz.bankapp.domain.model.Account;
import dev.nerviz.bankapp.domain.model.Actor;
import dev.nerviz.bankapp.infrastructure.rest.dto.AccountResponse;
import java.util.UUID;

/** Manual translation. No domain type in the controller's public signature. */
final class AccountMapper {

    /** The domain has a single currency, implicit in {@code Money}; the representation names it. */
    static final String CURRENCY = "USD";

    private AccountMapper() {}

    static OpenAccountCommand toCommand(UUID customerId, Actor actor) {
        return new OpenAccountCommand(customerId, actor);
    }

    static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.id().value(),
                account.owner().value(),
                account.balance().amount(),
                CURRENCY,
                account.status().name());
    }
}
