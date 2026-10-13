package dev.nerviz.bankapp.infrastructure.persistence.customer;

import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.CustomerIdentity;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Explicit translation in both directions. No persistence entity crosses the boundary. The
 * {@code null}s of an anonymized row live in the entity only: here they become the sealed
 * {@link CustomerIdentity}.
 */
final class CustomerPersistenceMapper {

    private CustomerPersistenceMapper() {}

    static CustomerEntity toEntity(Customer customer) {
        String securityNumber = null;
        LocalDate birthDate = null;
        Instant anonymizedAt = null;
        switch (customer.identity()) {
            case CustomerIdentity.Identified identified -> {
                securityNumber = identified.securityNumber().value();
                birthDate = identified.birthDate();
            }
            case CustomerIdentity.Anonymized anonymized -> anonymizedAt = anonymized.anonymizedAt();
        }
        return new CustomerEntity(
                customer.id().value(),
                customer.name(),
                securityNumber,
                birthDate,
                customer.registeredAt(),
                anonymizedAt,
                customer.status());
    }

    static Customer toDomain(CustomerEntity entity) {
        return Customer.rehydrate(
                CustomerId.of(entity.getId()),
                entity.getName(),
                identityOf(entity),
                entity.getRegisteredAt(),
                entity.getStatus());
    }

    private static CustomerIdentity identityOf(CustomerEntity entity) {
        if (entity.getAnonymizedAt() == null) {
            return CustomerIdentity.identified(SecurityNumber.of(entity.getSecurityNumber()), entity.getBirthDate());
        }
        return CustomerIdentity.anonymized(entity.getAnonymizedAt());
    }
}
