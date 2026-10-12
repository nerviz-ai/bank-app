package dev.nerviz.bankapp.domain.exception;

/**
 * Named subclass of {@link NotFoundException}: the same {@code errorCode} is raised at two
 * call sites — {@code GetCustomerUseCase} and {@code RecordKycVerdictUseCase}.
 */
public class CustomerNotFoundException extends NotFoundException {

    private static final String ERROR_CODE = "CUSTOMER_NOT_FOUND";

    public CustomerNotFoundException() {
        super(ERROR_CODE, "customer not found");
    }
}
