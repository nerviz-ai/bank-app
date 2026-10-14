package dev.nerviz.bankapp.infrastructure.messaging.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.domain.exception.ConcurrentUpdateException;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.converter.ConversionException;

/**
 * The classification is plain code; cheaper than forcing a lock failure through a broker.
 * {@code handleOne} returning {@code true} means the error handler dead-lettered the record;
 * {@code false} means the backoff still has attempts left and the record is redelivered.
 */
class KafkaListenerErrorHandlingTest {

    private static final String TOPIC = "bank-app.customer.kyc-verification-completed";
    private static final String DLQ = "bank-app.customer.kyc-verification-completed.dlq";

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final DefaultErrorHandler errorHandler =
            new KafkaListenerErrorHandling().kafkaListenerErrorHandler(kafkaTemplate, meterRegistry);

    private final ConsumerRecord<String, String> received = new ConsumerRecord<>(TOPIC, 0, 0L, "customer-id", "{}");
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);

    @BeforeEach
    void brokerAcceptsEverySend() {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
    }

    @Test
    void conflictIsDeadLetteredOnceWithoutRetry() {
        ConflictException duplicate = new ConflictException("KYC_VERDICT_CONFLICT", "opposite verdict recorded");

        boolean recovered = errorHandler.handleOne(duplicate, received, consumer, container);

        assertThat(recovered).isTrue();
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void notFoundIsDeadLetteredWithoutRetry() {
        boolean recovered = errorHandler.handleOne(new CustomerNotFoundException(), received, consumer, container);

        assertThat(recovered).isTrue();
    }

    @Test
    void concurrentUpdateIsRetried() {
        ConcurrentUpdateException lostRace = concurrentUpdate();

        boolean recovered = errorHandler.handleOne(lostRace, received, consumer, container);

        assertThat(recovered).isFalse();
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));
    }

    @Test
    void concurrentUpdateIsDeadLetteredOnceThreeRetriesAreSpent() {
        ConcurrentUpdateException lostRace = concurrentUpdate();
        errorHandler.handleOne(lostRace, received, consumer, container);
        errorHandler.handleOne(lostRace, received, consumer, container);
        errorHandler.handleOne(lostRace, received, consumer, container);

        boolean recovered = errorHandler.handleOne(lostRace, received, consumer, container);

        assertThat(recovered).isTrue();
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void unclassifiedFailureIsRetried() {
        boolean recovered = errorHandler.handleOne(
                new IllegalStateException("database unreachable"), received, consumer, container);

        assertThat(recovered).isFalse();
    }

    @Test
    void malformedPayloadIsDeadLetteredWithoutRetryAndCountedAsMalformed() {
        ConversionException malformed = new ConversionException("not json", new IllegalStateException());

        boolean recovered = errorHandler.handleOne(malformed, received, consumer, container);

        assertThat(recovered).isTrue();
        assertThat(meterRegistry
                        .counter(MonitoredDeadLetterPublisher.METRIC, "topic", DLQ, "reason", "MALFORMED")
                        .count())
                .isEqualTo(1.0);
    }

    @Test
    void deadLetterGoesToTheSamePartitionOfTheDlqWithTheErrorCodeHeader() {
        ArgumentCaptor<ProducerRecord<String, String>> sent = producerRecordCaptor();
        given(consumer.partitionsFor(eq(DLQ), any(Duration.class)))
                .willReturn(List.of(new PartitionInfo(DLQ, 0, null, null, null)));

        errorHandler.handleOne(
                new ConflictException("KYC_VERDICT_CONFLICT", "opposite verdict recorded"),
                received,
                consumer,
                container);

        verify(kafkaTemplate).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(DLQ);
        assertThat(sent.getValue().partition()).isZero();
        assertThat(new String(
                        sent.getValue()
                                .headers()
                                .lastHeader(MonitoredDeadLetterPublisher.ERROR_CODE_HEADER)
                                .value(),
                        StandardCharsets.UTF_8))
                .isEqualTo("KYC_VERDICT_CONFLICT");
    }

    @Test
    void deadLetterIsCountedByTopicAndReason() {
        errorHandler.handleOne(
                new ConflictException("KYC_VERDICT_CONFLICT", "opposite verdict recorded"),
                received,
                consumer,
                container);

        assertThat(meterRegistry
                        .counter(MonitoredDeadLetterPublisher.METRIC, "topic", DLQ, "reason", "KYC_VERDICT_CONFLICT")
                        .count())
                .isEqualTo(1.0);
    }

    private static ConcurrentUpdateException concurrentUpdate() {
        return new ConcurrentUpdateException(
                "CUSTOMER_CONCURRENT_UPDATE", "stale version", new IllegalStateException("optimistic lock"));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<ProducerRecord<String, String>> producerRecordCaptor() {
        return ArgumentCaptor.forClass(ProducerRecord.class);
    }
}
