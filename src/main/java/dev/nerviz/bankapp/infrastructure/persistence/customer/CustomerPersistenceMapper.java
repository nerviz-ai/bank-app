package dev.nerviz.bankapp.infrastructure.persistence.customer;

import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;

/** Explicit translation in both directions. No persistence entity crosses the boundary. */
final class CustomerPersistenceMapper {

    private CustomerPersistenceMapper() {}

    static CustomerEntity toEntity(Customer customer) {
        return new CustomerEntity(
                customer.id().value(),
                customer.name(),
                customer.securityNumber().value(),
                customer.birthDate(),
                customer.registeredAt());
    }

    static Customer toDomain(CustomerEntity entity) {
        return Customer.rehydrate(
                CustomerId.of(entity.getId()),
                entity.getName(),
                SecurityNumber.of(entity.getSecurityNumber()),
                entity.getBirthDate(),
                entity.getRegisteredAt());
    }
}
