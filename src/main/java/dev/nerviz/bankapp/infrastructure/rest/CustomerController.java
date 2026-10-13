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

/** Explicit version in the path — never {@code server.servlet.context-path}. */
@RestController
@RequestMapping(path = CustomerController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerController implements CustomerApi {

    public static final String BASE_PATH = "/api/v1/customers";

    private final CreateCustomerUseCase createCustomer;
    private final GetCustomerUseCase getCustomer;
    private final AnonymizeCustomerUseCase anonymizeCustomer;

    public CustomerController(
            CreateCustomerUseCase createCustomer,
            GetCustomerUseCase getCustomer,
            AnonymizeCustomerUseCase anonymizeCustomer) {
        this.createCustomer = createCustomer;
        this.getCustomer = getCustomer;
        this.anonymizeCustomer = anonymizeCustomer;
    }

    /**
     * {@code @Idempotent} wraps this method in the claim/replay transaction. 201 carries
     * {@code Location} + the created representation — a relative URI built from
     * {@code BASE_PATH} ({@.claude/rules/api-rest.md} § Success statuses).
     */
    @Override
    @Idempotent(bodyArgIndex = 0)
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerId id = createCustomer.create(CustomerMapper.toCommand(request));
        CustomerResponse body = CustomerMapper.toResponse(id);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + body.id())).body(body);
    }

    /**
     * {@code no-store}: the body carries a national identifier and a birth date in clear,
     * and must stay out of browser and intermediary caches.
     */
    @Override
    @GetMapping("/{customerId}")
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
    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> anonymize(@PathVariable UUID customerId) {
        try {
            anonymizeCustomer.anonymize(CustomerMapper.toAnonymizeCommand(customerId));
        } catch (CustomerNotFoundException e) {
            // answered as success by decision of UC-006 30-rest.md
        }
        return ResponseEntity.noContent().build();
    }
}
