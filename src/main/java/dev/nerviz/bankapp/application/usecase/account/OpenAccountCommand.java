package dev.nerviz.bankapp.application.usecase.account;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.Actor;
import java.util.UUID;

/**
 * Input for {@link OpenAccountUseCase}. JDK type for the customer id: the use case builds the
 * value object. Both fields are required; a missing one is rejected here, before the use case
 * runs.
 */
public record OpenAccountCommand(UUID customerId, Actor actor) {

    public OpenAccountCommand {
        if (customerId == null) {
            throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
        }
        if (actor == null) {
            throw new ValidationException("ACTOR_REQUIRED", "actor is required");
        }
    }
}
