package dev.nerviz.bankapp.application.usecase.customer;

import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import java.time.Clock;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete use case — no separate input interface ({@.claude/rules/naming.md} § Architecture
 * vocabulary). The read-write transaction opens and closes on {@link #anonymize}
 * ({@.claude/rules/architecture-ddd.md} § Application). Idempotent by state comparison: an
 * already-anonymized customer writes nothing.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnonymizeCustomerUseCase {

    CustomerRepository customerRepository;
    Clock clock;

    /**
     * @throws CustomerNotFoundException {@code CUSTOMER_NOT_FOUND} — no customer has this id
     * @throws BusinessRuleViolationException {@code CUSTOMER_KYC_IN_PROGRESS} — the KYC status is
     *     not final yet
     * @throws ConflictException {@code CUSTOMER_CONCURRENT_UPDATE} — another writer changed the
     *     row first
     */
    @Transactional
    public void anonymize(AnonymizeCustomerCommand command) {
        Customer customer = customerRepository
                .findById(CustomerId.of(command.customerId()))
                .orElseThrow(CustomerNotFoundException::new);
        Customer next = customer.anonymize(clock);
        if (!next.equals(customer)) {
            customerRepository.save(next);
        }
    }
}
