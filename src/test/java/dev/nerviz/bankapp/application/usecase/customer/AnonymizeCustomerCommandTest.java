package dev.nerviz.bankapp.application.usecase.customer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

class AnonymizeCustomerCommandTest {

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new AnonymizeCustomerCommand(null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_ID_REQUIRED");
    }
}
