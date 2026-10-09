package dev.nerviz.bankapp.infrastructure.messaging.kycverificationrequested;

import dev.nerviz.bankapp.domain.event.KycVerificationRequested;
import dev.nerviz.bankapp.infrastructure.messaging.outbox.OutboxPayload;
import java.time.Instant;
import java.time.LocalDate;

/**
 * The wire shape the KYC application receives, mapped explicitly from the domain event — the
 * event itself never serializes. {@code securityNumber}, {@code birthDate} and {@code name}
 * cross in full: the receiver verifies exactly those values (25-mensageria.md § 8,
 * {@code .claude/rules/personal-data.md} § Admitted exception). {@code toString()} masks the
 * first two, for log lines only.
 */
record KycVerificationRequestedPayload(
        String eventId, String customerId, String name, String securityNumber, LocalDate birthDate, Instant occurredAt)
        implements OutboxPayload {

    static KycVerificationRequestedPayload from(String eventId, KycVerificationRequested event) {
        return new KycVerificationRequestedPayload(
                eventId,
                event.customerId().value().toString(),
                event.name(),
                event.securityNumber().value(),
                event.birthDate(),
                event.occurredAt());
    }

    @Override
    public String toString() {
        return "KycVerificationRequestedPayload[eventId=" + eventId + ", customerId=" + customerId
                + ", securityNumber=***, birthDate=***, occurredAt=" + occurredAt + "]";
    }
}
