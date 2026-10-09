package dev.nerviz.bankapp.infrastructure.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nerviz.bankapp.CustomerFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerDetailsResponseTest {

    @Test
    void masksSecurityNumberAndBirthDateInToString() {
        CustomerDetailsResponse response = new CustomerDetailsResponse(
                UUID.randomUUID(),
                CustomerFixtures.NAME,
                CustomerFixtures.SECURITY_NUMBER,
                CustomerFixtures.BIRTH_DATE,
                CustomerFixtures.FIXED_CLOCK.instant(),
                "KYC_IN_PROGRESS");

        String rendered = response.toString();

        assertThat(rendered)
                .doesNotContain(CustomerFixtures.SECURITY_NUMBER)
                .doesNotContain(CustomerFixtures.BIRTH_DATE.toString())
                .contains(CustomerFixtures.NAME);
    }

    @Test
    void keepsStatusReadableInToString() {
        CustomerDetailsResponse response = new CustomerDetailsResponse(
                UUID.randomUUID(),
                CustomerFixtures.NAME,
                CustomerFixtures.SECURITY_NUMBER,
                CustomerFixtures.BIRTH_DATE,
                CustomerFixtures.FIXED_CLOCK.instant(),
                "KYC_IN_PROGRESS");

        assertThat(response.toString()).contains("KYC_IN_PROGRESS");
    }
}
