package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxEventSender;
import dev.nerviz.bankapp.application.port.OutboxSendResult;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * The only class in the project that imports {@code KafkaTemplate}. Key = aggregate id, so
 * one customer's events stay in order; value = the payload text, carried untouched from the
 * row. Waits for the broker acknowledgement, bounded by {@code delivery.timeout.ms} and
 * {@code max.block.ms}. Logs outcome and latency, never the payload.
 */
@Component
class KafkaOutboxEventSender implements OutboxEventSender {

    private static final Logger log = LoggerFactory.getLogger(KafkaOutboxEventSender.class);
    private static final double NANOS_PER_MILLI = 1_000_000d;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TopicResolver topics;

    KafkaOutboxEventSender(KafkaTemplate<String, String> kafkaTemplate, TopicResolver topics) {
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    @Override
    public OutboxSendResult send(OutboxEventRecord event) {
        Optional<String> topic = topics.forEventType(event.eventType());
        if (topic.isEmpty()) {
            return new OutboxSendResult.Unroutable("no topic mapped for " + event.eventType());
        }
        return sendTo(topic.get(), event);
    }

    private OutboxSendResult sendTo(String topic, OutboxEventRecord event) {
        long started = System.nanoTime();
        try {
            kafkaTemplate.send(topic, event.aggregateId(), event.payload()).join();
        } catch (CompletionException | KafkaException e) {
            String reason = reasonOf(e);
            log.warn(
                    "Outbox event send failed: eventId={} topic={} latencyMs={} reason={}",
                    event.eventId(),
                    topic,
                    millisSince(started),
                    reason);
            return new OutboxSendResult.Failed(reason);
        }
        log.info("Outbox event sent: eventId={} topic={} latencyMs={}", event.eventId(), topic, millisSince(started));
        return new OutboxSendResult.Sent();
    }

    private static String reasonOf(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static long millisSince(long startedNanos) {
        return (long) ((System.nanoTime() - startedNanos) / NANOS_PER_MILLI);
    }
}
