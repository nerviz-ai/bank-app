package dev.nerviz.bankapp.application.usecase.customer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.KycVerdict;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordKycVerdictCommandTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Test
    void rejectsMissingCustomerId() {
        assertThatThrownBy(() -> new RecordKycVerdictCommand(null, KycVerdict.APPROVED))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_ID_REQUIRED");
    }

    @Test
    void rejectsMissingVerdict() {
        assertThatThrownBy(() -> new RecordKycVerdictCommand(CUSTOMER_ID, null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("KYC_VERDICT_REQUIRED");
    }
}
