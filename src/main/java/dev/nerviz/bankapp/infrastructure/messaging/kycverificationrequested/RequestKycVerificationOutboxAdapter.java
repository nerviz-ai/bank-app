package dev.nerviz.bankapp.infrastructure.messaging.kycverificationrequested;

import dev.nerviz.bankapp.application.port.RequestKycVerification;
import dev.nerviz.bankapp.domain.event.KycVerificationRequested;
import dev.nerviz.bankapp.infrastructure.messaging.outbox.OutboxAppender;
import dev.nerviz.bankapp.infrastructure.messaging.outbox.TopicResolver;
import org.springframework.stereotype.Component;

/**
 * Records the request in the outbox instead of sending it. No {@code @Transactional}: the use
 * case owns the transaction, and a transaction of its own here would reopen the gap between
 * commit and publish that Form B closes.
 */
@Component
class RequestKycVerificationOutboxAdapter implements RequestKycVerification {

    private final OutboxAppender outbox;

    RequestKycVerificationOutboxAdapter(OutboxAppender outbox) {
        this.outbox = outbox;
    }

    @Override
    public void request(KycVerificationRequested event) {
        KycVerificationRequestedPayload payload = KycVerificationRequestedPayload.from(outbox.nextEventId(), event);
        outbox.append(payload.customerId(), TopicResolver.KYC_VERIFICATION_REQUESTED_EVENT_TYPE, payload);
    }
}
