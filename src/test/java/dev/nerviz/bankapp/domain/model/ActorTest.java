package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActorTest {

    private static final CustomerId SELF = CustomerId.of(UUID.randomUUID());
    private static final CustomerId OTHER = CustomerId.of(UUID.randomUUID());

    @Test
    void customerMayActOnlyForItself() {
        Actor actor = new Actor.Customer(SELF);

        assertThat(actor.mayActFor(SELF)).isTrue();
        assertThat(actor.mayActFor(OTHER)).isFalse();
    }

    @Test
    void operatorMayActForAnyone() {
        Actor actor = new Actor.Operator();

        assertThat(actor.mayActFor(SELF)).isTrue();
        assertThat(actor.mayActFor(OTHER)).isTrue();
    }

    @Test
    void customerActorRejectsMissingId() {
        assertThatThrownBy(() -> new Actor.Customer(null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_ID_REQUIRED");
    }
}
