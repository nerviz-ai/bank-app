package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import com.fasterxml.uuid.Generators;
import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxRelayGateway;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared by every flow that publishes through the outbox. Converts the payload record to JSON
 * text exactly once, here, and inserts the row through {@link OutboxRelayGateway}; from this
 * point the text is only carried.
 *
 * <p>No {@code @Transactional}: the use case owns the transaction and this class only has to
 * run inside it — the gateway refuses to append outside one.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OutboxAppender {

    private static final int INITIAL_ATTEMPTS = 0;

    OutboxRelayGateway gateway;
    JsonMapper jsonMapper;

    /** UUID v7, the row's identity and the id any receiver dedupes on. */
    public String nextEventId() {
        return Generators.timeBasedEpochGenerator().generate().toString();
    }

    /** {@code payload.eventId()} must come from {@link #nextEventId()}. */
    public void append(String aggregateId, String eventType, OutboxPayload payload) {
        gateway.append(new OutboxEventRecord(
                UUID.fromString(payload.eventId()),
                aggregateId,
                eventType,
                jsonMapper.writeValueAsString(payload),
                payload.occurredAt(),
                INITIAL_ATTEMPTS));
    }
}
