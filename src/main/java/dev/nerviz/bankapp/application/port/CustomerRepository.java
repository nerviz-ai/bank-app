package dev.nerviz.bankapp.application.port;

import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.util.Optional;

/**
 * Output port, kind persistence. No JPA, Spring or SQL types in the signatures — a
 * unique-constraint violation on {@code security_number} crosses as
 * {@code SecurityNumberAlreadyRegisteredException}, never as the framework exception that
 * caused it.
 */
public interface CustomerRepository {

    boolean existsBySecurityNumber(SecurityNumber securityNumber);

    Customer save(Customer customer);

    /** Absent is {@code Optional.empty()}; deciding that it is an error is the use case's. */
    Optional<Customer> findById(CustomerId id);
}
