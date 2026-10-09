package dev.nerviz.bankapp.infrastructure.messaging.kycverificationrequested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.domain.event.KycVerificationRequested;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.infrastructure.messaging.outbox.OutboxAppender;
import dev.nerviz.bankapp.infrastructure.messaging.outbox.OutboxPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequestKycVerificationOutboxAdapterTest {

    private static final String EVENT_ID = "0198a000-0000-7000-8000-000000000001";

    private final OutboxAppender outbox = mock(OutboxAppender.class);
    private final RequestKycVerificationOutboxAdapter adapter = new RequestKycVerificationOutboxAdapter(outbox);

    @Test
    void appendsPayloadWithEventTypeAndCustomerIdAsAggregateId() {
        Customer customer = CustomerFixtures.customer();
        given(outbox.nextEventId()).willReturn(EVENT_ID);

        adapter.request(KycVerificationRequested.of(customer));

        ArgumentCaptor<OutboxPayload> payload = ArgumentCaptor.forClass(OutboxPayload.class);
        verify(outbox).append(eq(customer.id().value().toString()), eq("KycVerificationRequested"), payload.capture());
        assertThat(payload.getValue().eventId()).isEqualTo(EVENT_ID);
    }

    @Test
    void payloadCarriesClearSecurityNumberAndBirthDate() {
        Customer customer = CustomerFixtures.customer();
        given(outbox.nextEventId()).willReturn(EVENT_ID);

        adapter.request(KycVerificationRequested.of(customer));

        ArgumentCaptor<OutboxPayload> payload = ArgumentCaptor.forClass(OutboxPayload.class);
        verify(outbox).append(eq(customer.id().value().toString()), eq("KycVerificationRequested"), payload.capture());
        assertThat(payload.getValue()).isInstanceOfSatisfying(KycVerificationRequestedPayload.class, wire -> {
            assertThat(wire.securityNumber()).isEqualTo(CustomerFixtures.SECURITY_NUMBER);
            assertThat(wire.birthDate()).isEqualTo(CustomerFixtures.BIRTH_DATE);
            assertThat(wire.name()).isEqualTo(CustomerFixtures.NAME);
            assertThat(wire.occurredAt()).isEqualTo(customer.registeredAt());
        });
    }
}
