package dev.nerviz.bankapp.application.usecase.customer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;

class GetCustomerCommandTest {

    @Test
    void rejectsNullId() {
        assertThatThrownBy(() -> new GetCustomerCommand(null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_ID_REQUIRED");
    }
}
