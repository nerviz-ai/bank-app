package dev.nerviz.bankapp.application.usecase.customer;

import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.NotFoundException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete use case — this blueprint's clean architecture has no separate input
 * interface ({@.claude/rules/naming.md} § Architecture vocabulary). The read-only
 * transaction opens and closes here ({@.claude/rules/architecture-ddd.md} § Application).
 */
@Service
public class GetCustomerUseCase {

    private final CustomerRepository customerRepository;

    public GetCustomerUseCase(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * @throws NotFoundException {@code CUSTOMER_NOT_FOUND} — no customer has this id
     */
    @Transactional(readOnly = true)
    public Customer get(GetCustomerCommand command) {
        return customerRepository
                .findById(CustomerId.of(command.id()))
                .orElseThrow(() -> new NotFoundException("CUSTOMER_NOT_FOUND", "customer not found"));
    }
}
