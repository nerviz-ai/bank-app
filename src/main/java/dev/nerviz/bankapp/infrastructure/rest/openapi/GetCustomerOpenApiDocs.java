package dev.nerviz.bankapp.infrastructure.rest.openapi;

import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerDetailsResponse;
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

/** Goes on {@code CustomerApi#get} alone — {@code @GetMapping} lives on the controller. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "Returns one customer by id",
        operationId = "getCustomer",
        parameters = {
            @Parameter(
                    name = "customerId",
                    in = ParameterIn.PATH,
                    required = true,
                    description = "Customer id as returned by createCustomer",
                    example = "018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c11")
        })
@ApiResponse(
        responseCode = "200",
        description = "Customer found",
        content = @Content(schema = @Schema(implementation = CustomerDetailsResponse.class)))
@ApiResponse(
        responseCode = "400",
        description = "customerId is not a UUID",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "404",
        description = "No customer with this id (errorCode CUSTOMER_NOT_FOUND)",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(
        responseCode = "500",
        description = "Unmapped error",
        content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public @interface GetCustomerOpenApiDocs {}
