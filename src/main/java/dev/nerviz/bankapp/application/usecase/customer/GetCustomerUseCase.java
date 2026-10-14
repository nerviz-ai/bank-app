package dev.nerviz.bankapp.application.usecase.customer;

import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete use case — this blueprint's clean architecture has no separate input
 * interface ({@.claude/rules/naming.md} § Architecture vocabulary). The read-only
 * transaction opens and closes here ({@.claude/rules/architecture-ddd.md} § Application).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GetCustomerUseCase {

    CustomerRepository customerRepository;

    /**
     * @throws CustomerNotFoundException {@code CUSTOMER_NOT_FOUND} — no customer has this id
     */
    @Transactional(readOnly = true)
    public Customer get(GetCustomerCommand command) {
        return customerRepository.findById(CustomerId.of(command.id())).orElseThrow(CustomerNotFoundException::new);
    }
}
