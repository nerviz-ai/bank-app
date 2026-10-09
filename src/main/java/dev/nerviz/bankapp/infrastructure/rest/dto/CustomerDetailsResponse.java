package dev.nerviz.bankapp.infrastructure.rest.dto;

import dev.nerviz.bankapp.commons.logging.annotations.MaskSensitiveData;
import dev.nerviz.bankapp.commons.logging.enums.MaskedType;
import dev.nerviz.bankapp.commons.logging.interfaces.LogMask;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Full representation of a customer. {@code securityNumber} and {@code birthDate} leave the
 * process in clear, by the user's decision recorded in the use case. Implements {@link LogMask}: the
 * global REST aspect logs every response by default, and both fields are masking candidates
 * ({@.claude/rules/logging.md} § Masking candidates) — the annotations cover the log line, not the body.
 */
@Schema(description = "Registration data of a customer")
public record CustomerDetailsResponse(
        @Schema(
                description = "Opaque customer identifier",
                example = "018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c11",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "Full name", example = "Maria Silva", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(
                description = "National identification number, 11 digits",
                example = "12345678901",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @MaskSensitiveData(maskedType = MaskedType.DOCUMENT)
        String securityNumber,

        @Schema(
                description = "Birth date, ISO-8601",
                example = "1990-05-17",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @MaskSensitiveData(maskedType = MaskedType.DATE)
        LocalDate birthDate,

        @Schema(
                description = "Registration instant, ISO-8601 UTC",
                example = "2026-10-08T12:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant registeredAt,

        @Schema(
                description = "KYC lifecycle status of the customer",
                allowableValues = {"KYC_IN_PROGRESS", "ACTIVE", "REJECTED_BY_KYC"},
                example = "KYC_IN_PROGRESS",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status)
        implements LogMask {

    @Override
    public String toString() {
        return mask(this);
    }
}
