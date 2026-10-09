package dev.nerviz.bankapp.application.usecase.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.port.OutboxRetentionGateway;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PruneOutboxEventsUseCaseTest {

    private static final int BATCH = 500;

    private final OutboxRetentionGateway gateway = mock(OutboxRetentionGateway.class);
    private final PruneOutboxEventsUseCase useCase =
            new PruneOutboxEventsUseCase(gateway, CustomerFixtures.FIXED_CLOCK);

    @Test
    void deletesInBatchesUntilShortBatch() {
        given(gateway.deletePublishedBefore(any(), anyInt())).willReturn(BATCH, BATCH, 120);

        useCase.prunePublished(new PruneOutboxEventsCommand(Duration.ofDays(7), BATCH));

        verify(gateway, times(3)).deletePublishedBefore(any(), anyInt());
    }

    @Test
    void usesCutoffOfNowMinusRetention() {
        given(gateway.deletePublishedBefore(any(), anyInt())).willReturn(0);

        useCase.prunePublished(new PruneOutboxEventsCommand(Duration.ofDays(7), BATCH));

        verify(gateway).deletePublishedBefore(Instant.parse("2026-01-08T10:00:00Z"), BATCH);
    }

    @Test
    void returnsTotalDeleted() {
        given(gateway.deletePublishedBefore(any(), anyInt())).willReturn(BATCH, 120);

        long total = useCase.prunePublished(new PruneOutboxEventsCommand(Duration.ofDays(7), BATCH));

        assertThat(total).isEqualTo(620);
    }
}
