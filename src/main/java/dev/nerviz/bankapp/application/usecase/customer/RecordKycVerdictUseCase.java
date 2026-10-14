package dev.nerviz.bankapp.application.usecase.customer;

import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete use case — no separate input interface ({@.claude/rules/naming.md} § Architecture
 * vocabulary). The read-write transaction opens and closes on {@link #recordVerdict}
 * ({@.claude/rules/architecture-ddd.md} § Application). Idempotent by state comparison: a
 * verdict already applied writes nothing.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecordKycVerdictUseCase {

    CustomerRepository customerRepository;

    /**
     * @throws CustomerNotFoundException {@code CUSTOMER_NOT_FOUND} — no customer has this id
     * @throws ConflictException {@code KYC_VERDICT_CONFLICT} — the opposite verdict was already
     *     recorded; {@code CUSTOMER_CONCURRENT_UPDATE} — another writer changed the row first
     */
    @Transactional
    public void recordVerdict(RecordKycVerdictCommand command) {
        Customer customer = customerRepository
                .findById(CustomerId.of(command.customerId()))
                .orElseThrow(CustomerNotFoundException::new);
        Customer next = customer.recordKycVerdict(command.verdict());
        if (next.status() != customer.status()) {
            customerRepository.save(next);
        }
    }
}
