package dev.nerviz.bankapp.application.port;

import dev.nerviz.bankapp.domain.model.Account;

/**
 * Output port, kind persistence. No JPA, Spring or SQL types in the signatures: a persistence
 * failure crosses as a domain exception, never as the framework exception that caused it.
 */
public interface AccountRepository {

    Account save(Account account);
}
