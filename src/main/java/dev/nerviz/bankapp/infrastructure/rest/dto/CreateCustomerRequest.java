package dev.nerviz.bankapp.infrastructure.rest.dto;

import dev.nerviz.bankapp.commons.logging.annotations.MaskSensitiveData;
import dev.nerviz.bankapp.commons.logging.enums.MaskedType;
import dev.nerviz.bankapp.commons.logging.interfaces.LogMask;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Presence only ({@code @NotNull}): every format rule is a domain invariant with its own
 * {@code errorCode} ({@.claude/rules/api-rest.md} § Errors — 400 family). Implements
 * {@link LogMask}: the global REST aspect logs every DTO by default, and
 * {@code securityNumber}/{@code birthDate} are masking candidates
 * ({@.claude/rules/logging.md} § Masking candidates).
 */
@Schema(description = "Data to create a customer")
public record CreateCustomerRequest(
        @Schema(
                description = "Full name, trimmed",
                example = "Maria Silva",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        String name,

        @Schema(
                description = "National identification number, exactly 11 digits",
                example = "12345678901",
                pattern = "^[0-9]{11}$",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @MaskSensitiveData(maskedType = MaskedType.DOCUMENT)
        String securityNumber,

        @Schema(
                description = "Birth date, ISO-8601",
                example = "1990-05-17",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @MaskSensitiveData(maskedType = MaskedType.DATE)
        LocalDate birthDate)
        implements LogMask {

    @Override
    public String toString() {
        return mask(this);
    }
}
