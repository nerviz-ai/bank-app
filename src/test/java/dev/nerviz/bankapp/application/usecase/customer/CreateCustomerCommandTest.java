package dev.nerviz.bankapp.application.usecase.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nerviz.bankapp.CustomerFixtures;
import org.junit.jupiter.api.Test;

class CreateCustomerCommandTest {

    @Test
    void toStringMasksSecurityNumberAndBirthDate() {
        CreateCustomerCommand command = CustomerFixtures.command();

        assertThat(command.toString())
                .doesNotContain(CustomerFixtures.SECURITY_NUMBER)
                .doesNotContain(CustomerFixtures.BIRTH_DATE.toString());
    }
}
