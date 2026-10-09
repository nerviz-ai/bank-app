package dev.nerviz.bankapp.application.usecase.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PruneOutboxEventsCommandTest {

    @Test
    void acceptsPositiveValues() {
        PruneOutboxEventsCommand command = new PruneOutboxEventsCommand(Duration.ofDays(7), 500);

        assertThat(command.batchSize()).isEqualTo(500);
    }

    @ParameterizedTest
    @MethodSource("invalidCommands")
    void rejectsInvalidValues(Duration retention, int batchSize, String errorCode) {
        assertThatThrownBy(() -> new PruneOutboxEventsCommand(retention, batchSize))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    static Stream<Arguments> invalidCommands() {
        return Stream.of(
                Arguments.of(Duration.ZERO, 500, "OUTBOX_RETENTION_INVALID"),
                Arguments.of(Duration.ofDays(-1), 500, "OUTBOX_RETENTION_INVALID"),
                Arguments.of(null, 500, "OUTBOX_RETENTION_INVALID"),
                Arguments.of(Duration.ofDays(7), 0, "OUTBOX_PRUNE_BATCH_SIZE_INVALID"));
    }
}
