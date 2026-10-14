package dev.nerviz.bankapp.infrastructure.messaging.listener;

import dev.nerviz.bankapp.domain.exception.DomainException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wraps the {@link org.springframework.kafka.listener.DeadLetterPublishingRecoverer}: logs one
 * {@code WARN} line and increments the dead-letter counter before delegating the actual publish
 * (25-messaging.md § 4, {@code .claude/rules/observability.md} § Metrics — "a delivery the
 * design declared must not be lost carries two metrics"; here the give-up path itself).
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class DeadLetterMonitoringRecoverer implements ConsumerRecordRecoverer {

    private static final String UNKNOWN = "unknown";
    private static final String MALFORMED_REASON = "MALFORMED";
    private static final String TRANSIENT_EXHAUSTED_REASON = "TRANSIENT_EXHAUSTED";
    private static final String METRIC_NAME = "kyc.verdict.dead.lettered";

    ConsumerRecordRecoverer delegate;
    MeterRegistry meterRegistry;
    JsonMapper jsonMapper;

    @Override
    public void accept(ConsumerRecord<?, ?> consumerRecord, Exception exception) {
        String reason = reasonOf(exception);
        log.warn(
                "kafka record dead-lettered: eventId={} customerId={} reason={}",
                eventIdOf(consumerRecord),
                consumerRecord.key(),
                reason);
        meterRegistry.counter(METRIC_NAME, "reason", reason).increment();
        delegate.accept(consumerRecord, exception);
    }

    private String eventIdOf(ConsumerRecord<?, ?> consumerRecord) {
        Object value = consumerRecord.value();
        if (value == null) {
            return UNKNOWN;
        }
        try {
            Map<?, ?> payload = jsonMapper.readValue(value.toString(), Map.class);
            Object eventId = payload.get("eventId");
            return eventId == null ? UNKNOWN : eventId.toString();
        } catch (JacksonException ex) {
            log.warn(
                    "Dead-lettered record payload unreadable, eventId logged as unknown: topic={} partition={} offset={} errorCode={}",
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    MALFORMED_REASON);
            return UNKNOWN;
        }
    }

    private static String reasonOf(Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof DomainException domainException) {
                return domainException.errorCode();
            }
            if (isConversionFailure(cause)) {
                return MALFORMED_REASON;
            }
            cause = cause.getCause();
        }
        return TRANSIENT_EXHAUSTED_REASON;
    }

    private static boolean isConversionFailure(Throwable cause) {
        return cause instanceof MessageConversionException || cause instanceof DeserializationException;
    }
}
