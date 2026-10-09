package dev.nerviz.bankapp.infrastructure.messaging.kycverificationrequested;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.domain.event.KycVerificationRequested;
import dev.nerviz.bankapp.domain.model.Customer;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KycVerificationRequestedPayloadTest {

    private static final String EVENT_ID = "0198a000-0000-7000-8000-000000000001";

    @Test
    void toStringMasksSecurityNumberAndBirthDate() {
        KycVerificationRequestedPayload payload = payloadOf(CustomerFixtures.customer());

        String rendered = payload.toString();

        assertThat(rendered)
                .doesNotContain(CustomerFixtures.SECURITY_NUMBER)
                .doesNotContain(CustomerFixtures.BIRTH_DATE.toString())
                .contains("securityNumber=***")
                .contains("birthDate=***")
                .contains(EVENT_ID);
    }

    @Test
    void toStringOmitsTheName() {
        KycVerificationRequestedPayload payload = payloadOf(CustomerFixtures.customer());

        assertThat(payload.toString()).doesNotContain(CustomerFixtures.NAME);
    }

    @Test
    void fromCopiesEveryFieldOfTheEvent() {
        Customer customer = CustomerFixtures.customer();

        KycVerificationRequestedPayload payload = payloadOf(customer);

        assertThat(payload)
                .isEqualTo(new KycVerificationRequestedPayload(
                        EVENT_ID,
                        customer.id().value().toString(),
                        CustomerFixtures.NAME,
                        CustomerFixtures.SECURITY_NUMBER,
                        CustomerFixtures.BIRTH_DATE,
                        Instant.parse("2026-01-15T10:00:00Z")));
    }

    private static KycVerificationRequestedPayload payloadOf(Customer customer) {
        return KycVerificationRequestedPayload.from(EVENT_ID, KycVerificationRequested.of(customer));
    }
}
