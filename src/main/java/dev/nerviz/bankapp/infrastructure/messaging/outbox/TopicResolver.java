package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Event type to topic name. A lookup, not a decision: the topic of an event is fixed by
 * {@code .claude/rules/messaging.md} § Topics and serialization and never computed from the
 * payload. An event type absent here is unroutable — the relay dead-letters it at once.
 */
@Component
public class TopicResolver {

    public static final String KYC_VERIFICATION_REQUESTED_EVENT_TYPE = "KycVerificationRequested";
    public static final String KYC_VERIFICATION_REQUESTED_TOPIC = "bank-app.customer.kyc-verification-requested";

    private static final Map<String, String> TOPICS =
            Map.of(KYC_VERIFICATION_REQUESTED_EVENT_TYPE, KYC_VERIFICATION_REQUESTED_TOPIC);

    public Optional<String> forEventType(String eventType) {
        return Optional.ofNullable(TOPICS.get(eventType));
    }
}
