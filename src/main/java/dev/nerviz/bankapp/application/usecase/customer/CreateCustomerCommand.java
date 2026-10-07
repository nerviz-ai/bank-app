package dev.nerviz.bankapp.application.usecase.customer;

import java.time.LocalDate;

/**
 * Input for {@link CreateCustomerUseCase}. Primitive and JDK types: the command is where
 * the outside world hasn't been validated yet — the use case builds the value objects.
 * {@code toString()} masks {@code securityNumber} and {@code birthDate}
 * ({@.claude/rules/logging.md} § Masking candidates).
 */
public record CreateCustomerCommand(String name, String securityNumber, LocalDate birthDate) {

    @Override
    public String toString() {
        return "CreateCustomerCommand[name=" + name + ", securityNumber=***, birthDate=***]";
    }
}
