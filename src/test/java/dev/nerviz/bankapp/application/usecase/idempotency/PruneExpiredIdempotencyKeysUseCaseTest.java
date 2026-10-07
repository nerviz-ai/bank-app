package dev.nerviz.bankapp.application.usecase.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.port.IdempotencyKeyPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Orchestration only: the loop over {@link IdempotencyKeyPort#deleteExpired}, with the port
 * replaced by a double. The batch statement itself is proven against the real engine in
 * {@code IdempotencyKeyStoreIT}.
 */
class PruneExpiredIdempotencyKeysUseCaseTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T10:00:00Z");

    private final IdempotencyKeyPort idempotencyKeyPort = mock(IdempotencyKeyPort.class);
    private final PruneExpiredIdempotencyKeysUseCase useCase =
            new PruneExpiredIdempotencyKeysUseCase(idempotencyKeyPort, CustomerFixtures.FIXED_CLOCK);

    @Test
    void returnsZeroAndCallsOnceWhenNothingExpired() {
        given(idempotencyKeyPort.deleteExpired(any(), anyInt())).willReturn(0);

        long deleted = useCase.prune(new PruneExpiredIdempotencyKeysCommand(1000, 100));

        assertThat(deleted).isZero();
        verify(idempotencyKeyPort, times(1)).deleteExpired(any(), anyInt());
    }

    @Test
    void stopsAfterShortBatch() {
        given(idempotencyKeyPort.deleteExpired(any(), eq(1000))).willReturn(1000, 1000, 3);

        long deleted = useCase.prune(new PruneExpiredIdempotencyKeysCommand(1000, 100));

        assertThat(deleted).isEqualTo(2003);
        verify(idempotencyKeyPort, times(3)).deleteExpired(any(), eq(1000));
    }

    @Test
    void stopsAtMaxBatchesWhenEveryBatchIsFull() {
        given(idempotencyKeyPort.deleteExpired(any(), eq(100))).willReturn(100);

        long deleted = useCase.prune(new PruneExpiredIdempotencyKeysCommand(100, 4));

        assertThat(deleted).isEqualTo(400);
        verify(idempotencyKeyPort, times(4)).deleteExpired(any(), eq(100));
    }

    @Test
    void usesOneCutoffFromTheClockForEveryBatch() {
        given(idempotencyKeyPort.deleteExpired(any(), eq(100))).willReturn(100, 100, 7);
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);

        useCase.prune(new PruneExpiredIdempotencyKeysCommand(100, 100));

        verify(idempotencyKeyPort, times(3)).deleteExpired(cutoffCaptor.capture(), eq(100));
        assertThat(cutoffCaptor.getAllValues()).allMatch(FIXED_INSTANT::equals);
    }

    @Test
    void propagatesPortFailureWithoutFurtherCalls() {
        given(idempotencyKeyPort.deleteExpired(any(), eq(1000)))
                .willReturn(1000)
                .willThrow(new IllegalStateException("boom"));
        PruneExpiredIdempotencyKeysCommand command = new PruneExpiredIdempotencyKeysCommand(1000, 100);

        assertThatThrownBy(() -> useCase.prune(command)).isInstanceOf(IllegalStateException.class);

        verify(idempotencyKeyPort, times(2)).deleteExpired(any(), eq(1000));
    }
}
