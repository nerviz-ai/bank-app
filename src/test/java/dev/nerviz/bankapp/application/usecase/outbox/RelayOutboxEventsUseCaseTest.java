package dev.nerviz.bankapp.application.usecase.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxEventSender;
import dev.nerviz.bankapp.application.port.OutboxFailure;
import dev.nerviz.bankapp.application.port.OutboxRelayGateway;
import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import dev.nerviz.bankapp.application.port.OutboxSendResult;
import dev.nerviz.bankapp.application.port.RelayOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelayOutboxEventsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final OutboxRetryPolicy POLICY =
            new OutboxRetryPolicy(10, Duration.ofSeconds(1), Duration.ofMinutes(5));
    private static final RelayOutboxEventsCommand COMMAND =
            new RelayOutboxEventsCommand(10, Duration.ofMinutes(8), POLICY);

    private final OutboxRelayGateway gateway = mock(OutboxRelayGateway.class);
    private final OutboxEventSender sender = mock(OutboxEventSender.class);
    private final RelayOutboxEventsUseCase useCase =
            new RelayOutboxEventsUseCase(gateway, sender, CustomerFixtures.FIXED_CLOCK);

    @Test
    void sendsEachClaimedRecordAndMarksItPublished() {
        OutboxEventRecord first = event("0198a000-0000-7000-8000-000000000001");
        OutboxEventRecord second = event("0198a000-0000-7000-8000-000000000002");
        given(gateway.claimPending(10, NOW, Duration.ofMinutes(8))).willReturn(List.of(first, second));
        given(sender.send(any())).willReturn(new OutboxSendResult.Sent());

        RelayOutcome outcome = useCase.relayPending(COMMAND);

        assertThat(outcome).isEqualTo(new RelayOutcome(2, 0, 0));
        verify(gateway).markPublished(first.eventId(), NOW);
        verify(gateway).markPublished(second.eventId(), NOW);
    }

    @Test
    void recordsFailureAndContinuesWithNextRecord() {
        OutboxEventRecord failing = event("0198a000-0000-7000-8000-000000000001");
        OutboxEventRecord healthy = event("0198a000-0000-7000-8000-000000000002");
        given(gateway.claimPending(10, NOW, Duration.ofMinutes(8))).willReturn(List.of(failing, healthy));
        given(sender.send(failing)).willReturn(new OutboxSendResult.Failed("broker down"));
        given(sender.send(healthy)).willReturn(new OutboxSendResult.Sent());
        given(gateway.recordFailure(any(), eq(POLICY))).willReturn(false);

        RelayOutcome outcome = useCase.relayPending(COMMAND);

        assertThat(outcome).isEqualTo(new RelayOutcome(1, 1, 0));
        verify(gateway).recordFailure(new OutboxFailure(failing.eventId(), "broker down", NOW), POLICY);
        verify(gateway).markPublished(healthy.eventId(), NOW);
    }

    @Test
    void countsDeadLetteredRecordsInOutcome() {
        OutboxEventRecord exhausted = event("0198a000-0000-7000-8000-000000000001");
        OutboxEventRecord unroutable = event("0198a000-0000-7000-8000-000000000002");
        given(gateway.claimPending(10, NOW, Duration.ofMinutes(8))).willReturn(List.of(exhausted, unroutable));
        given(sender.send(exhausted)).willReturn(new OutboxSendResult.Failed("broker down"));
        given(sender.send(unroutable)).willReturn(new OutboxSendResult.Unroutable("no topic"));
        given(gateway.recordFailure(any(), eq(POLICY))).willReturn(true);

        RelayOutcome outcome = useCase.relayPending(COMMAND);

        assertThat(outcome).isEqualTo(new RelayOutcome(0, 0, 2));
        verify(gateway).markDeadLettered(unroutable.eventId(), "no topic");
    }

    @Test
    void returnsEmptyOutcomeWhenNothingIsClaimed() {
        given(gateway.claimPending(10, NOW, Duration.ofMinutes(8))).willReturn(List.of());

        RelayOutcome outcome = useCase.relayPending(COMMAND);

        assertThat(outcome).isEqualTo(RelayOutcome.none());
        verify(sender, never()).send(any());
    }

    @Test
    void reportsOldestPendingAge() {
        given(gateway.oldestPendingAge(NOW)).willReturn(Optional.of(Duration.ofSeconds(90)));

        Optional<Duration> age = useCase.oldestPendingAge();

        assertThat(age).contains(Duration.ofSeconds(90));
    }

    private static OutboxEventRecord event(String id) {
        return new OutboxEventRecord(UUID.fromString(id), "customer-id", "KycVerificationRequested", "{}", NOW, 0);
    }
}
