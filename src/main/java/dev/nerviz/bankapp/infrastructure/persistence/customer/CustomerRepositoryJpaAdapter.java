package dev.nerviz.bankapp.infrastructure.persistence.customer;

import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.ConcurrentUpdateException;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.CustomerIdentity;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * No {@code @Transactional} here — the use case owns the transaction
 * ({@.claude/rules/persistence.md} § Boundary).
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class CustomerRepositoryJpaAdapter implements CustomerRepository {

    private static final String SECURITY_NUMBER_CONSTRAINT = "uq_customers_security_number";

    CustomerJpaRepository repository;

    @Override
    public boolean existsBySecurityNumber(SecurityNumber securityNumber) {
        return repository.existsBySecurityNumber(securityNumber.value());
    }

    /**
     * Load-and-mutate: an existing id is loaded and its status and anonymization applied, so the entity keeps the
     * row's real version; an absent id is inserted. {@code saveAndFlush}, not {@code save}: the
     * statement must reach the database inside this try for the constraint or lock failure to
     * surface here, not at the transaction's commit.
     */
    @Override
    public Customer save(Customer customer) {
        try {
            CustomerEntity entity = repository
                    .findById(customer.id().value())
                    .map(existing -> {
                        existing.changeStatus(customer.status());
                        if (customer.identity() instanceof CustomerIdentity.Anonymized anonymized
                                && existing.getAnonymizedAt() == null) {
                            existing.anonymize(customer.name(), anonymized.anonymizedAt());
                        }
                        return existing;
                    })
                    .orElseGet(() -> CustomerPersistenceMapper.toEntity(customer));
            return CustomerPersistenceMapper.toDomain(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException cause) {
            throw translate(cause);
        } catch (ObjectOptimisticLockingFailureException cause) {
            throw concurrentUpdate(cause);
        }
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return repository.findById(id.value()).map(CustomerPersistenceMapper::toDomain);
    }

    private static ConcurrentUpdateException concurrentUpdate(ObjectOptimisticLockingFailureException cause) {
        return new ConcurrentUpdateException("CUSTOMER_CONCURRENT_UPDATE", "customer was changed concurrently", cause);
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
