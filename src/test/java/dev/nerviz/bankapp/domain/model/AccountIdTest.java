package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountIdTest {

    @Test
    void rejectsNullValue() {
        assertThatThrownBy(() -> AccountId.of(null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("ACCOUNT_ID_REQUIRED");
    }

    @Test
    void keepsValueAndPrintsIt() {
        UUID value = UUID.fromString("018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c22");

        AccountId id = AccountId.of(value);

        assertThat(id.value()).isEqualTo(value);
        assertThat(id).hasToString("018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c22");
    }
}
