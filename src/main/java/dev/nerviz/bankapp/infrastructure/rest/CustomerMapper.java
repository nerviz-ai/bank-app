package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.application.usecase.customer.AnonymizeCustomerCommand;
import dev.nerviz.bankapp.application.usecase.customer.CreateCustomerCommand;
import dev.nerviz.bankapp.application.usecase.customer.GetCustomerCommand;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.CustomerIdentity;
import dev.nerviz.bankapp.infrastructure.rest.dto.CreateCustomerRequest;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerDetailsResponse;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerResponse;
import java.util.UUID;

/** Manual translation. No domain type in the controller's public signature. */
final class CustomerMapper {

    private CustomerMapper() {}

    static CreateCustomerCommand toCommand(CreateCustomerRequest request) {
        return new CreateCustomerCommand(request.name(), request.securityNumber(), request.birthDate());
    }

    static CustomerResponse toResponse(CustomerId id) {
        return new CustomerResponse(id.value());
    }

    static GetCustomerCommand toGetCommand(UUID customerId) {
        return new GetCustomerCommand(customerId);
    }

    static AnonymizeCustomerCommand toAnonymizeCommand(UUID customerId) {
        return new AnonymizeCustomerCommand(customerId);
    }

    /** {@code Anonymized} leaves both values {@code null}; {@code NON_NULL} drops them from the body. */
    static CustomerDetailsResponse toDetailsResponse(Customer customer) {
        return switch (customer.identity()) {
            case CustomerIdentity.Identified identified ->
                new CustomerDetailsResponse(
                        customer.id().value(),
                        customer.name(),
                        identified.securityNumber().value(),
                        identified.birthDate(),
                        customer.registeredAt(),
                        customer.status().name());
            case CustomerIdentity.Anonymized ignored ->
                new CustomerDetailsResponse(
                        customer.id().value(),
                        customer.name(),
                        null,
                        null,
                        customer.registeredAt(),
                        customer.status().name());
        };
    }
}
