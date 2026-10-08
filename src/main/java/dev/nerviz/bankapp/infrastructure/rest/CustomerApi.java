package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.infrastructure.rest.dto.CreateCustomerRequest;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerDetailsResponse;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerResponse;
import dev.nerviz.bankapp.infrastructure.rest.openapi.CreateCustomerOpenApiDocs;
import dev.nerviz.bankapp.infrastructure.rest.openapi.GetCustomerOpenApiDocs;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/** Contract interface: OpenAPI documentation only — no Spring mapping or binding here. */
@Tag(name = "Customers")
public interface CustomerApi {

    @CreateCustomerOpenApiDocs
    ResponseEntity<CustomerResponse> create(CreateCustomerRequest request);

    @GetCustomerOpenApiDocs
    ResponseEntity<CustomerDetailsResponse> get(UUID customerId);
}
