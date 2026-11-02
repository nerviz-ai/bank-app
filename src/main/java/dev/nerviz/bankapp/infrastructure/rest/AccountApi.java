package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.infrastructure.rest.dto.AccountResponse;
import dev.nerviz.bankapp.infrastructure.rest.openapi.OpenAccountOpenApiDocs;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/** Contract interface: OpenAPI documentation only — no Spring mapping or binding here. */
@Tag(name = "Accounts")
public interface AccountApi {

    @OpenAccountOpenApiDocs
    ResponseEntity<AccountResponse> open(UUID customerId, Authentication authentication);
}
