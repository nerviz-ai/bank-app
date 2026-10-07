package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.application.usecase.customer.CreateCustomerUseCase;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.infrastructure.rest.dto.CreateCustomerRequest;
import dev.nerviz.bankapp.infrastructure.rest.dto.CustomerResponse;
import dev.nerviz.bankapp.infrastructure.rest.idempotent.Idempotent;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    public CustomerController(CreateCustomerUseCase createCustomer) {
        this.createCustomer = createCustomer;
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
}
