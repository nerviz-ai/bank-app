package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxSendResult;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaOutboxEventSenderTest {

    private static final String TOPIC = "bank-app.customer.kyc-verification-requested";
    private static final String PAYLOAD = "{\"eventId\":\"x\"}";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final KafkaOutboxEventSender sender = new KafkaOutboxEventSender(kafkaTemplate, new TopicResolver());

    @Test
    void sendsPayloadTextKeyedByAggregateIdToTheResolvedTopic() {
        given(kafkaTemplate.send(TOPIC, "customer-1", PAYLOAD))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        OutboxSendResult result = sender.send(event("KycVerificationRequested"));

        assertThat(result).isInstanceOf(OutboxSendResult.Sent.class);
        verify(kafkaTemplate).send(TOPIC, "customer-1", PAYLOAD);
    }

    @Test
    void reportsFailureWhenTheBrokerDoesNotAcknowledge() {
        given(kafkaTemplate.send(TOPIC, "customer-1", PAYLOAD))
                .willReturn(CompletableFuture.failedFuture(new TimeoutException("delivery timed out")));

        OutboxSendResult result = sender.send(event("KycVerificationRequested"));

        assertThat(result)
                .isInstanceOfSatisfying(
                        OutboxSendResult.Failed.class,
                        failed -> assertThat(failed.reason())
                                .contains("TimeoutException")
                                .contains("delivery timed out"));
    }

    @Test
    void reportsFailureWhenTheClientRefusesToSend() {
        willThrow(new KafkaException("producer closed")).given(kafkaTemplate).send(TOPIC, "customer-1", PAYLOAD);

        OutboxSendResult result = sender.send(event("KycVerificationRequested"));

        assertThat(result)
                .isInstanceOfSatisfying(
                        OutboxSendResult.Failed.class,
                        failed -> assertThat(failed.reason())
                                .contains("KafkaException")
                                .contains("producer closed"));
    }

    @Test
    void reportsUnroutableAndSendsNothingForAnUnmappedEventType() {
        OutboxSendResult result = sender.send(event("SomethingElseHappened"));

        assertThat(result)
                .isInstanceOfSatisfying(
                        OutboxSendResult.Unroutable.class,
                        unroutable -> assertThat(unroutable.reason()).contains("SomethingElseHappened"));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private static OutboxEventRecord event(String eventType) {
        return new OutboxEventRecord(
                UUID.fromString("0198a000-0000-7000-8000-000000000001"),
                "customer-1",
                eventType,
                PAYLOAD,
                Instant.parse("2026-01-15T10:00:00Z"),
                0);
    }
}
