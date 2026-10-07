package dev.nerviz.bankapp.application.usecase.customer;

import com.fasterxml.uuid.Generators;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete use case — this blueprint's clean architecture has no separate input
 * interface ({@.claude/rules/naming.md} § Architecture vocabulary). The transaction opens
 * and closes here ({@.claude/rules/architecture-ddd.md} § Application).
 */
@Service
public class CreateCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final Clock clock;

    public CreateCustomerUseCase(CustomerRepository customerRepository, Clock clock) {
        this.customerRepository = customerRepository;
        this.clock = clock;
    }

    /**
     * @throws dev.nerviz.bankapp.domain.exception.ValidationException malformed input
     * @throws dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException underage customer
     * @throws SecurityNumberAlreadyRegisteredException security number already registered
     */
    @Transactional
    public CustomerId create(CreateCustomerCommand command) {
        SecurityNumber securityNumber = SecurityNumber.of(command.securityNumber());
        if (customerRepository.existsBySecurityNumber(securityNumber)) {
            throw new SecurityNumberAlreadyRegisteredException("security number already registered");
        }
        CustomerId id = CustomerId.of(Generators.timeBasedEpochGenerator().generate());
        Customer customer = Customer.register(id, command.name(), securityNumber, command.birthDate(), clock);
        return customerRepository.save(customer).id();
    }
}
