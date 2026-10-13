package dev.nerviz.bankapp.application.usecase.customer;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.util.UUID;

/**
 * Input for {@link AnonymizeCustomerUseCase}. JDK type at the input: the use case builds the
 * value object. A {@code null} id is rejected here, before the use case runs.
 */
public record AnonymizeCustomerCommand(UUID customerId) {

    public AnonymizeCustomerCommand {
        if (customerId == null) {
            throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
        }
    }
}
