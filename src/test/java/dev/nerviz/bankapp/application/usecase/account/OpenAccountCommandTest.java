package dev.nerviz.bankapp.application.usecase.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.Actor;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenAccountCommandTest {

    @Test
    void rejectsMissingCustomerId() {
        Actor actor = new Actor.Operator();

        assertThatThrownBy(() -> new OpenAccountCommand(null, actor))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_ID_REQUIRED");
    }

    @Test
    void rejectsMissingActor() {
        UUID customerId = UUID.randomUUID();

        assertThatThrownBy(() -> new OpenAccountCommand(customerId, null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("ACTOR_REQUIRED");
    }

    @Test
    void keepsBothFields() {
        UUID customerId = UUID.randomUUID();
        Actor actor = new Actor.Operator();

        OpenAccountCommand command = new OpenAccountCommand(customerId, actor);

        assertThat(command.customerId()).isEqualTo(customerId);
        assertThat(command.actor()).isEqualTo(actor);
    }
}
