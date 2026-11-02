package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.application.usecase.account.OpenAccountUseCase;
import dev.nerviz.bankapp.infrastructure.rest.dto.AccountResponse;
import dev.nerviz.bankapp.infrastructure.rest.idempotent.Idempotent;
import java.net.URI;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The class mapping is the resource alone: the {@code /api/v{version}} prefix comes from
 * {@code ApiVersioningConfig}. The request has no body, so {@code @Idempotent} hashes the
 * first argument, the customer id.
 */
@RestController
@RequestMapping(path = "/customers/{customerId}/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AccountController implements AccountApi {

    private static final String CUSTOMERS_SEGMENT = "/customers/";

    OpenAccountUseCase openAccount;
    CurrentActorResolver actors;

    /**
     * Role in the filter chain; ownership in the use case. 201 carries a relative {@code Location}
     * built from the prefix this request arrived on, so a v2 call answers a v2 location.
     */
    @Override
    @Idempotent
    @PostMapping(version = "1+")
    public ResponseEntity<AccountResponse> open(@PathVariable UUID customerId, Authentication authentication) {
        AccountResponse body = AccountMapper.toResponse(
                openAccount.open(AccountMapper.toCommand(customerId, actors.resolve(authentication))));
        return ResponseEntity.created(locationOf(body.id())).body(body);
    }

    private static URI locationOf(UUID accountId) {
        String path =
                ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
        String prefix = path.substring(0, path.indexOf(CUSTOMERS_SEGMENT));
        return UriComponentsBuilder.fromPath(prefix)
                .pathSegment("accounts", accountId.toString())
                .build()
                .toUri();
    }
}
