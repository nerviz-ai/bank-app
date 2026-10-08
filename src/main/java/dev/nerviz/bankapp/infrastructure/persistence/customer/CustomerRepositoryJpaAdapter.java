package dev.nerviz.bankapp.infrastructure.persistence.customer;

import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * No {@code @Transactional} here — the use case owns the transaction
 * ({@.claude/rules/persistence.md} § Boundary).
 */
@Component
class CustomerRepositoryJpaAdapter implements CustomerRepository {

    private static final String SECURITY_NUMBER_CONSTRAINT = "uq_customers_security_number";

    private final CustomerJpaRepository repository;

    CustomerRepositoryJpaAdapter(CustomerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsBySecurityNumber(SecurityNumber securityNumber) {
        return repository.existsBySecurityNumber(securityNumber.value());
    }

    /**
     * {@code saveAndFlush}, not {@code save}: the INSERT must reach the database inside
     * this try for the constraint violation to surface here, not at the transaction's
     * commit.
     */
    @Override
    public Customer save(Customer customer) {
        try {
            return CustomerPersistenceMapper.toDomain(
                    repository.saveAndFlush(CustomerPersistenceMapper.toEntity(customer)));
        } catch (DataIntegrityViolationException cause) {
            throw translate(cause);
        }
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return repository.findById(id.value()).map(CustomerPersistenceMapper::toDomain);
    }

    private static RuntimeException translate(DataIntegrityViolationException cause) {
        if (constraintName(cause).map(SECURITY_NUMBER_CONSTRAINT::equals).orElse(false)) {
            return new SecurityNumberAlreadyRegisteredException("security number already registered", cause);
        }
        return new ConflictException(
                "CUSTOMER_PERSISTENCE_CONFLICT", "customer could not be persisted due to a conflict", cause);
    }

    private static Optional<String> constraintName(DataIntegrityViolationException cause) {
        if (cause.getCause() instanceof ConstraintViolationException constraintViolation) {
            return Optional.ofNullable(constraintViolation.getConstraintName());
        }
        return Optional.empty();
    }
}
