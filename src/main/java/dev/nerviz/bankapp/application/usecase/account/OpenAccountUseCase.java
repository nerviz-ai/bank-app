package dev.nerviz.bankapp.application.usecase.account;

import com.fasterxml.uuid.Generators;
import dev.nerviz.bankapp.application.port.AccountRepository;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Account;
import dev.nerviz.bankapp.domain.model.AccountId;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete use case — this blueprint's clean architecture has no separate input
 * interface ({@.claude/rules/naming.md} § Architecture vocabulary). The transaction opens
 * and closes here ({@.claude/rules/architecture-ddd.md} § Application); the customer read and
 * the account insert share it. Ownership is decided here: a customer that is not the actor's
 * answers like an absent one, so a response never reveals that another customer exists.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OpenAccountUseCase {

    CustomerRepository customerRepository;
    AccountRepository accountRepository;

    /**
     * @throws CustomerNotFoundException {@code CUSTOMER_NOT_FOUND} — absent, or not the actor's
     * @throws BusinessRuleViolationException {@code CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT}
     */
    @Transactional
    public Account open(OpenAccountCommand command) {
        CustomerId customerId = CustomerId.of(command.customerId());
        Customer customer = customerRepository
                .findById(customerId)
                .filter(found -> command.actor().mayActFor(found.id()))
                .orElseThrow(CustomerNotFoundException::new);
        Account account =
                Account.open(AccountId.of(Generators.timeBasedEpochGenerator().generate()), customer);
        return accountRepository.save(account);
    }
}
