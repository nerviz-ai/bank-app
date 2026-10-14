package dev.nerviz.bankapp.application.usecase.customer;

import com.fasterxml.uuid.Generators;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.application.port.RequestKycVerification;
import dev.nerviz.bankapp.domain.event.KycVerificationRequested;
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Clock;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Concrete use case — this blueprint's clean architecture has no separate input
 * interface ({@.claude/rules/naming.md} § Architecture vocabulary). The transaction opens
 * and closes here ({@.claude/rules/architecture-ddd.md} § Application).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CreateCustomerUseCase {

    CustomerRepository customerRepository;
    RequestKycVerification requestKycVerification;
    Clock clock;

    /**
     * @throws dev.nerviz.bankapp.domain.exception.ValidationException malformed input
     * @throws dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException underage customer
     * @throws SecurityNumberAlreadyRegisteredException security number already registered
     *     (raised before the KYC request is recorded; a failure to record it rolls the creation back)
     */
    @Transactional
    public CustomerId create(CreateCustomerCommand command) {
        SecurityNumber securityNumber = SecurityNumber.of(command.securityNumber());
        if (customerRepository.existsBySecurityNumber(securityNumber)) {
            throw new SecurityNumberAlreadyRegisteredException("security number already registered");
        }
        CustomerId id = CustomerId.of(Generators.timeBasedEpochGenerator().generate());
        Customer customer = Customer.register(id, command.name(), securityNumber, command.birthDate(), clock);
        Customer saved = customerRepository.save(customer);
        requestKycVerification.request(KycVerificationRequested.of(saved));
        return saved.id();
    }
}
