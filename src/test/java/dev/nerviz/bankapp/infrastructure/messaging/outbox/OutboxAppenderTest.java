package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxRelayGateway;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class OutboxAppenderTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T10:00:00Z");

    private final OutboxRelayGateway gateway = mock(OutboxRelayGateway.class);
    private final OutboxAppender appender =
            new OutboxAppender(gateway, JsonMapper.builder().build());

    @Test
    void generatesTimeOrderedUuidAsEventId() {
        String eventId = appender.nextEventId();

        assertThat(UUID.fromString(eventId).version()).isEqualTo(7);
    }

    @Test
    void generatesDistinctEventIds() {
        assertThat(appender.nextEventId()).isNotEqualTo(appender.nextEventId());
    }

    @Test
    void appendsRowWithPayloadAsJsonTextUnderTheEventId() {
        String eventId = "0198a000-0000-7000-8000-000000000001";

        appender.append("customer-1", "SomethingHappened", new SamplePayload(eventId, "value", OCCURRED_AT));

        ArgumentCaptor<OutboxEventRecord> appended = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(gateway).append(appended.capture());
        assertThat(appended.getValue()).satisfies(row -> {
            assertThat(row.eventId()).isEqualTo(UUID.fromString(eventId));
            assertThat(row.aggregateId()).isEqualTo("customer-1");
            assertThat(row.eventType()).isEqualTo("SomethingHappened");
            assertThat(row.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(row.attempts()).isZero();
            assertThat(row.payload())
                    .startsWith("{")
                    .contains("\"eventId\":\"" + eventId + "\"")
                    .contains("\"field\":\"value\"")
                    .contains("\"occurredAt\":\"2026-01-15T10:00:00Z\"");
        });
    }

    private record SamplePayload(String eventId, String field, Instant occurredAt) implements OutboxPayload {}
}
