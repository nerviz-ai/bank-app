package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.application.usecase.customer.AnonymizeCustomerUseCase;
import dev.nerviz.bankapp.application.usecase.customer.CreateCustomerUseCase;
import dev.nerviz.bankapp.application.usecase.customer.GetCustomerUseCase;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.infrastructure.rest.dto.CreateCustomerRequest;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerDetailsResponse;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerResponse;
import dev.nerviz.bankapp.infrastructure.rest.idempotent.Idempotent;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The class mapping is the resource alone: the {@code /api/v{version}} prefix comes from
 * {@code ApiVersioningConfig}, never {@code server.servlet.context-path}. Every handler declares
 * its baseline version ({@.claude/rules/api-rest.md} § Versioning).
 */
@Slf4j
@RestController
@RequestMapping(path = "/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CustomerController implements CustomerApi {

    CreateCustomerUseCase createCustomer;
    GetCustomerUseCase getCustomer;
    AnonymizeCustomerUseCase anonymizeCustomer;

    /**
     * {@code @Idempotent} wraps this method in the claim/replay transaction. 201 carries
     * {@code Location} + the created representation — a relative URI built from the path this
     * request arrived on, so a v2 call answers a v2 location ({@.claude/rules/api-rest.md}
     * § Success statuses).
     */
    @Override
    @Idempotent(bodyArgIndex = 0)
    @PostMapping(version = "1+", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerId id = createCustomer.create(CustomerMapper.toCommand(request));
        CustomerResponse body = CustomerMapper.toResponse(id);
        return ResponseEntity.created(locationOf(body.id().toString())).body(body);
    }

    /**
     * {@code no-store}: the body carries a national identifier and a birth date in clear,
     * and must stay out of browser and intermediary caches.
     */
    @Override
    @GetMapping(path = "/{customerId}", version = "1+")
    public ResponseEntity<CustomerDetailsResponse> get(@PathVariable UUID customerId) {
        CustomerDetailsResponse body =
                CustomerMapper.toDetailsResponse(getCustomer.get(CustomerMapper.toGetCommand(customerId)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    /**
     * 204 for success, for an already anonymized customer and for an unknown id: the whole API
     * answers {@code DELETE} of a missing resource with 204 ({@.claude/rules/api-rest.md} § Success
     * statuses). The not-found is caught here, not in {@code ApiExceptionHandler}, so {@code GET}
     * keeps its 404 for the same exception.
     */
    @Override
    @DeleteMapping(path = "/{customerId}", version = "1+")
    public ResponseEntity<Void> anonymize(@PathVariable UUID customerId) {
        try {
            anonymizeCustomer.anonymize(CustomerMapper.toAnonymizeCommand(customerId));
        } catch (CustomerNotFoundException e) {
            log.warn(
                    "Anonymize answered 204 for an unknown customer, by decision of UC-006 30-rest.md: customerId={} errorCode={}",
                    customerId,
                    e.errorCode());
        }
        return ResponseEntity.noContent().build();
    }

    private static URI locationOf(String id) {
        String collection =
                ServletUriComponentsBuilder.fromCurrentRequestUri().build().getPath();
        return UriComponentsBuilder.fromPath(collection).pathSegment(id).build().toUri();
    }
}
