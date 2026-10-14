package dev.nerviz.bankapp.infrastructure.messaging.listener;

import dev.nerviz.bankapp.domain.exception.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;

/**
 * Sends to {@code <topic>.dlq}, same partition, with the failure's {@code errorCode} in a header,
 * and logs one {@code WARN} line and counts {@code kyc.verdict.dead.lettered} (tags {@code topic},
 * {@code reason}) for every record that lands there — the "monitored, not archived" of
 * {@code .claude/rules/messaging.md} § Retry and DLQ. It counts at publish time, on the
 * destination name, so a non-blocking retry topic's hops are never counted.
 */
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class MonitoredDeadLetterPublisher extends DeadLetterPublishingRecoverer {

    static final String DLQ_SUFFIX = ".dlq";
    static final String ERROR_CODE_HEADER = "errorCode";
    static final String METRIC = "kyc.verdict.dead.lettered";
    private static final String MALFORMED = "MALFORMED";
    private static final String TRANSIENT_EXHAUSTED = "TRANSIENT_EXHAUSTED";

    MeterRegistry meterRegistry;

    MonitoredDeadLetterPublisher(
            Function<ProducerRecord<?, ?>, ? extends KafkaOperations<?, ?>> templateResolver,
            BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> destinationResolver,
            MeterRegistry meterRegistry) {
        super(templateResolver, destinationResolver);
        this.meterRegistry = meterRegistry;
    }

    /** {@code <topic>.dlq}, the source partition — the blocking shape's destination. */
    static TopicPartition sameTopicDlq(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
        return new TopicPartition(consumerRecord.topic() + DLQ_SUFFIX, consumerRecord.partition());
    }

    /** The failure's code as a header, so a DLQ reader needs no stack trace to know the reason. */
    static Headers errorCodeHeader(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
        RecordHeaders headers = new RecordHeaders();
        headers.add(ERROR_CODE_HEADER, reasonOf(exception).getBytes(StandardCharsets.UTF_8));
        return headers;
    }

    @Override
    protected void publish(
            ProducerRecord<Object, Object> outRecord,
            KafkaOperations<Object, Object> kafkaTemplate,
            ConsumerRecord<?, ?> inRecord) {
        if (outRecord.topic().endsWith(DLQ_SUFFIX)) {
            String reason = headerValue(outRecord.headers().lastHeader(ERROR_CODE_HEADER));
            log.warn(
                    "kafka record dead-lettered: topic={} partition={} offset={} key={} reason={}",
                    inRecord.topic(),
                    inRecord.partition(),
                    inRecord.offset(),
                    inRecord.key(),
                    reason);
            meterRegistry
                    .counter(METRIC, "topic", outRecord.topic(), "reason", reason)
                    .increment();
        }
        super.publish(outRecord, kafkaTemplate, inRecord);
    }

    /**
     * The failure's code, walking its causes. A malformed payload is {@code MALFORMED}:
     * {@code ConversionException} is what {@code StringJacksonJsonMessageConverter} throws on text
     * that does not parse, the other two a deserializer's and a {@code @Payload} conversion's.
     */
    private static String reasonOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DomainException domain) {
                return domain.errorCode();
            }
            if (cause instanceof ConversionException
                    || cause instanceof MessageConversionException
                    || cause instanceof DeserializationException) {
                return MALFORMED;
            }
        }
        return TRANSIENT_EXHAUSTED;
    }

    private static String headerValue(Header header) {
        return header == null ? TRANSIENT_EXHAUSTED : new String(header.value(), StandardCharsets.UTF_8);
    }
}
