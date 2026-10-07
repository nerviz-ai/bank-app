package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.application.usecase.customer.CreateCustomerCommand;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.infrastructure.rest.dto.CreateCustomerRequest;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerResponse;

/** Manual translation. No domain type in the controller's public signature. */
final class CustomerMapper {

    private CustomerMapper() {}

    static CreateCustomerCommand toCommand(CreateCustomerRequest request) {
        return new CreateCustomerCommand(request.name(), request.securityNumber(), request.birthDate());
    }

    static CustomerResponse toResponse(CustomerId id) {
        return new CustomerResponse(id.value());
    }
}
