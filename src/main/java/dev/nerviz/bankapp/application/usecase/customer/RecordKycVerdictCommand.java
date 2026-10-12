package dev.nerviz.bankapp.application.usecase.customer;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.KycVerdict;
import java.util.UUID;

/**
 * Input for {@link RecordKycVerdictUseCase}. JDK type for the id: the use case builds the
 * value object. A {@code null} in either component is rejected here, before the use case runs.
 */
public record RecordKycVerdictCommand(UUID customerId, KycVerdict verdict) {

    public RecordKycVerdictCommand {
        if (customerId == null) {
            throw new ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required");
        }
        if (verdict == null) {
            throw new ValidationException("KYC_VERDICT_REQUIRED", "kyc verdict is required");
        }
    }
}
