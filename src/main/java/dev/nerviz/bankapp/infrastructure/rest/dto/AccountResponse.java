package dev.nerviz.bankapp.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

/** Ids, an amount, a currency and a status: no masking candidate, so no {@code LogMask}. */
@Schema(description = "Representation of an account")
public record AccountResponse(
        @Schema(
                description = "Opaque account identifier",
                example = "018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c22",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(
                description = "Identifier of the customer that owns the account",
                example = "018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c11",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID customerId,

        @Schema(
                description = "Current balance, two decimals",
                example = "0.00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal balance,

        @Schema(description = "Currency of the balance", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
        String currency,

        @Schema(
                description = "Account status",
                example = "ACTIVE",
                allowableValues = {"ACTIVE", "INACTIVE"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String status) {}
