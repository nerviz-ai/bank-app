package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicResolverTest {

    private final TopicResolver resolver = new TopicResolver();

    @Test
    void resolvesKycVerificationRequestedTopic() {
        assertThat(resolver.forEventType("KycVerificationRequested"))
                .contains("bank-app.customer.kyc-verification-requested");
    }

    @Test
    void rejectsUnknownEventType() {
        assertThat(resolver.forEventType("SomethingElseHappened")).isEmpty();
    }
}
