package dev.nerviz.bankapp.infrastructure.rest.openapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.http.ProblemDetail;

/** Goes on {@code CustomerApi#anonymize} alone — {@code @DeleteMapping} lives on the controller. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "Anonymizes one customer by id",
        operationId = "anonymizeCustomer",
        parameters = {
            @Parameter(
                    name = "customerId",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Customer id as returned by createCustomer",
                    example = "018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c11")
        })
@ApiResponse(
        responseCode = "204",
        description = "Customer anonymized, already anonymized, or no customer with this id. No body")
@ApiResponse(
        responseCode = "400",
        description = "customerId is not a UUID",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "401",
        description = "No token, or the token is malformed, expired, or from another issuer or audience",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "403",
        description = "The token does not carry the DATA_PROTECTION_OFFICER role",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "409",
        description = "Another writer changed the customer first (errorCode CUSTOMER_CONCURRENT_UPDATE)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "422",
        description = "KYC verification still in progress (errorCode CUSTOMER_KYC_IN_PROGRESS)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "500",
        description = "Unmapped error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public @interface AnonymizeCustomerOpenApiDocs {}
