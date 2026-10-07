package dev.nerviz.bankapp.infrastructure.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nerviz.bankapp.CustomerFixtures;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CreateCustomerRequestTest {

    @Test
    void maskedRepresentationHidesSecurityNumberAndBirthDate() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                CustomerFixtures.NAME, CustomerFixtures.SECURITY_NUMBER, CustomerFixtures.BIRTH_DATE);

        String rendered = request.toString();

        assertThat(rendered)
                .doesNotContain(CustomerFixtures.SECURITY_NUMBER)
                .doesNotContain(LocalDate.of(1990, 5, 17).toString())
                .contains(CustomerFixtures.NAME);
    }
}
