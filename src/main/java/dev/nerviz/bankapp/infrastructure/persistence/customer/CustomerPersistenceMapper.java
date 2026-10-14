package dev.nerviz.bankapp.infrastructure.persistence.customer;

import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.CustomerIdentity;
import dev.nerviz.bankapp.domain.model.SecurityNumber;

/**
 * Explicit translation in both directions. No persistence entity crosses the boundary. The
 * {@code null}s of an anonymized row live in the entity only: here they become the sealed
 * {@link CustomerIdentity}.
 */
final class CustomerPersistenceMapper {

    private CustomerPersistenceMapper() {}

    static CustomerEntity toEntity(Customer customer) {
        return switch (customer.identity()) {
            case CustomerIdentity.Identified identified -> identifiedEntity(customer, identified);
            case CustomerIdentity.Anonymized anonymized -> anonymizedEntity(customer, anonymized);
        };
    }

    private static CustomerEntity identifiedEntity(Customer customer, CustomerIdentity.Identified identified) {
        return new CustomerEntity(
                customer.id().value(),
                customer.name(),
                identified.securityNumber().value(),
                identified.birthDate(),
                customer.registeredAt(),
                null,
                customer.status());
    }

    private static CustomerEntity anonymizedEntity(Customer customer, CustomerIdentity.Anonymized anonymized) {
        return new CustomerEntity(
                customer.id().value(),
                customer.name(),
                null,
                null,
                customer.registeredAt(),
                anonymized.anonymizedAt(),
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
