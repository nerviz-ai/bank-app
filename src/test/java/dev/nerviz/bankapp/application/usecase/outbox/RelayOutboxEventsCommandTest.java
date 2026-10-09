package dev.nerviz.bankapp.application.usecase.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelayOutboxEventsCommandTest {

    private static final OutboxRetryPolicy POLICY =
            new OutboxRetryPolicy(10, Duration.ofSeconds(1), Duration.ofMinutes(5));
    private static final Duration LEASE = Duration.ofMinutes(8);

    @Test
    void acceptsPositiveValues() {
        RelayOutboxEventsCommand command = new RelayOutboxEventsCommand(10, LEASE, POLICY);

        assertThat(command.batchSize()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveBatchSize(int batchSize) {
        assertThatThrownBy(() -> new RelayOutboxEventsCommand(batchSize, LEASE, POLICY))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("OUTBOX_BATCH_SIZE_INVALID");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void rejectsNonPositiveLease(long seconds) {
        Duration lease = Duration.ofSeconds(seconds);

        assertThatThrownBy(() -> new RelayOutboxEventsCommand(10, lease, POLICY))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("OUTBOX_LEASE_INVALID");
    }

    @Test
    void rejectsMissingLease() {
        assertThatThrownBy(() -> new RelayOutboxEventsCommand(10, null, POLICY))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("OUTBOX_LEASE_INVALID");
    }

    @Test
    void rejectsMissingRetryPolicy() {
        assertThatThrownBy(() -> new RelayOutboxEventsCommand(10, LEASE, null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("OUTBOX_RETRY_POLICY_REQUIRED");
    }
}
