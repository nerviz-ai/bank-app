package dev.nerviz.bankapp.application.usecase.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PruneExpiredIdempotencyKeysCommandTest {

    @Test
    void acceptsMinimumValues() {
        PruneExpiredIdempotencyKeysCommand command = new PruneExpiredIdempotencyKeysCommand(1, 1);

        assertThat(command.batchSize()).isEqualTo(1);
        assertThat(command.maxBatches()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsBatchSizeBelowOne(int invalid) {
        assertThatThrownBy(() -> new PruneExpiredIdempotencyKeysCommand(invalid, 1))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("PRUNE_BATCH_SIZE_INVALID");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsMaxBatchesBelowOne(int invalid) {
        assertThatThrownBy(() -> new PruneExpiredIdempotencyKeysCommand(1, invalid))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("PRUNE_MAX_BATCHES_INVALID");
    }
}
