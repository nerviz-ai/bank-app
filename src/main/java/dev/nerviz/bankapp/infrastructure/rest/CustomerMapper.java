package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.application.usecase.customer.CreateCustomerCommand;
import dev.nerviz.bankapp.application.usecase.customer.GetCustomerCommand;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
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

    static CustomerDetailsResponse toDetailsResponse(Customer customer) {
        return new CustomerDetailsResponse(
                customer.id().value(),
                customer.name(),
                customer.securityNumber().value(),
                customer.birthDate(),
                customer.registeredAt());
    }
}
