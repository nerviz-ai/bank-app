package dev.nerviz.bankapp.infrastructure.messaging.kycverificationrequested;

import dev.nerviz.bankapp.application.port.RequestKycVerification;
import dev.nerviz.bankapp.domain.event.KycVerificationRequested;
import dev.nerviz.bankapp.infrastructure.messaging.outbox.OutboxAppender;
import dev.nerviz.bankapp.infrastructure.messaging.outbox.TopicResolver;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

/**
 * Records the request in the outbox instead of sending it. No {@code @Transactional}: the use
 * case owns the transaction, and a transaction of its own here would reopen the gap between
 * commit and publish that Form B closes.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class RequestKycVerificationOutboxAdapter implements RequestKycVerification {

    OutboxAppender outbox;

    @Override
    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = TopicResolver.KYC_VERIFICATION_REQUESTED_TOPIC,
                            description = "Asks the KYC application to verify a customer. Published when a customer"
                                    + " needs KYC; partition key: customerId (string), so one customer's events"
                                    + " stay ordered.",
                            payloadType = KycVerificationRequestedPayload.class,
                            message =
                                    @AsyncMessage(
                                            messageId = "KycVerificationRequested",
                                            name = "KycVerificationRequested"),
                            headers = @AsyncOperation.Headers(notUsed = true)))
    public void request(KycVerificationRequested event) {
        KycVerificationRequestedPayload payload = KycVerificationRequestedPayload.from(outbox.nextEventId(), event);
        outbox.append(payload.customerId(), TopicResolver.KYC_VERIFICATION_REQUESTED_EVENT_TYPE, payload);
    }
}
