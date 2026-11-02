package dev.nerviz.bankapp.infrastructure.persistence.account;

import dev.nerviz.bankapp.application.port.AccountRepository;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.model.Account;
import java.time.Clock;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * No {@code @Transactional} here — the use case owns the transaction
 * ({@.claude/rules/persistence.md} § Boundary). Insert only: this case never updates an account,
 * and a movement that does will add its own write path. {@code opened_at} is stamped from the
 * injected {@link Clock}.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class AccountRepositoryJpaAdapter implements AccountRepository {

    AccountJpaRepository repository;
    Clock clock;

    /**
     * {@code saveAndFlush}, not {@code save}: the INSERT must reach the database inside this
     * try for a constraint failure (the foreign key to the owner, the checks) to surface here,
     * not at the transaction's commit.
     */
    @Override
    public Account save(Account account) {
        try {
            AccountEntity entity = AccountPersistenceMapper.toEntity(account, clock.instant());
            return AccountPersistenceMapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException cause) {
            throw new ConflictException(
                    "ACCOUNT_PERSISTENCE_CONFLICT", "account could not be persisted due to a conflict", cause);
        }
    }
}
