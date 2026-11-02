package dev.nerviz.bankapp.infrastructure.persistence.account;

import dev.nerviz.bankapp.domain.model.Account;
import dev.nerviz.bankapp.domain.model.AccountId;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.Money;
import java.time.Instant;

/**
 * Explicit translation in both directions. No persistence entity crosses the boundary.
 * {@code openedAt} is the adapter's: the domain has no such field, so it comes in as a
 * parameter and is never mapped back.
 */
final class AccountPersistenceMapper {

    private AccountPersistenceMapper() {}

    static AccountEntity toEntity(Account account, Instant openedAt) {
        return new AccountEntity(
                account.id().value(), account.owner().value(), account.balance().amount(), account.status(), openedAt);
    }

    static Account toDomain(AccountEntity entity) {
        return Account.rehydrate(
                AccountId.of(entity.getId()),
                CustomerId.of(entity.getCustomerId()),
                Money.of(entity.getBalance()),
                entity.getStatus());
    }
}
