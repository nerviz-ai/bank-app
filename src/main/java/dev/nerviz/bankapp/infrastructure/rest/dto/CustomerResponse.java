package dev.nerviz.bankapp.infrastructure.rest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Reduced representation: an aggregate's {@code id} alone — on the counter-list of masking candidates. */
@Schema(description = "Representation of a created customer")
public record CustomerResponse(
        @Schema(
                description = "Opaque customer identifier",
                example = "018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c11",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id) {}
