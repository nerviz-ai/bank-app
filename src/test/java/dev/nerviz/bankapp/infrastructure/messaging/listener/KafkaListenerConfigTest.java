package dev.nerviz.bankapp.infrastructure.messaging.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * The classifier is plain code; cheaper than forcing a lock failure through a broker
 * ({@code 40-testes.md} § 1). {@code handleOne} returning {@code true} means the error handler
 * recovered (dead-lettered) on the first failure, with no retry; {@code false} means the normal
 * backoff still has attempts left.
 */
class KafkaListenerConfigTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final DefaultErrorHandler errorHandler =
            new KafkaListenerConfig().kafkaListenerErrorHandler(kafkaTemplate, new SimpleMeterRegistry(), jsonMapper);

    private final ConsumerRecord<String, String> record =
            new ConsumerRecord<>("bank-app.customer.kyc-verification-completed", 0, 0L, "customer-id", "{}");
    private final Consumer<?, ?> consumer = mock(Consumer.class);
    private final MessageListenerContainer container = mock(MessageListenerContainer.class);

    @Test
    void domainExceptionsAreNotRetryable() {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        boolean customerNotFoundRecovered =
                errorHandler.handleOne(new CustomerNotFoundException(), record, consumer, container);
        boolean conflictRecovered = errorHandler.handleOne(
                new ConflictException("KYC_VERDICT_CONFLICT", "conflict"), record, consumer, container);

        assertThat(customerNotFoundRecovered).isTrue();
        assertThat(conflictRecovered).isTrue();
    }

    /**
     * The container's classifier matches by Java type, not {@code errorCode}
     * ({@link KafkaListenerConfig}'s Javadoc): it cannot single out
     * {@code CUSTOMER_CONCURRENT_UPDATE} from every other {@link ConflictException}, so it
     * recovers (dead-letters) on the first failure here too. The retry that errorCode gets is
     * the listener's own, in process, before the exception ever reaches this handler.
     */
    @Test
    void concurrentUpdateIsNotRetryableHereEither() {
        given(kafkaTemplate.send(any(ProducerRecord.class)))
                .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        boolean recovered = errorHandler.handleOne(
                new ConflictException("CUSTOMER_CONCURRENT_UPDATE", "stale version"), record, consumer, container);

        assertThat(recovered).isTrue();
    }
}
