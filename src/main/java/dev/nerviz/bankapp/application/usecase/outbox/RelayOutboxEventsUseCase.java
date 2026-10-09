package dev.nerviz.bankapp.application.usecase.outbox;

import dev.nerviz.bankapp.application.port.OutboxEventRecord;
import dev.nerviz.bankapp.application.port.OutboxEventSender;
import dev.nerviz.bankapp.application.port.OutboxFailure;
import dev.nerviz.bankapp.application.port.OutboxRelayGateway;
import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import dev.nerviz.bankapp.application.port.OutboxSendResult;
import dev.nerviz.bankapp.application.port.RelayOutcome;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One relay pass: claim a batch, send each row, mark it published or record the failure,
 * dead-letter when exhausted. Concrete use case — no separate input interface.
 *
 * <p>No {@code @Transactional}: claim, send and mark are separate units by design — the
 * gateway opens one per call, and a transaction held across a broker send is the long
 * transaction the outbox exists to avoid. The send may well have succeeded when a mark fails:
 * the row is sent again, and the receiver dedupes on the stable event id.
 */
@Service
public class RelayOutboxEventsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RelayOutboxEventsUseCase.class);

    private final OutboxRelayGateway relayGateway;
    private final OutboxEventSender sender;
    private final Clock clock;

    public RelayOutboxEventsUseCase(OutboxRelayGateway relayGateway, OutboxEventSender sender, Clock clock) {
        this.relayGateway = relayGateway;
        this.sender = sender;
        this.clock = clock;
    }

    /** Never throws for a single row's failure: one poisoned row must not stall the batch. */
    public RelayOutcome relayPending(RelayOutboxEventsCommand command) {
        List<OutboxEventRecord> claimed =
                relayGateway.claimPending(command.batchSize(), clock.instant(), command.lease());
        RelayOutcome outcome = RelayOutcome.none();
        for (OutboxEventRecord event : claimed) {
            outcome = outcome.plus(relay(event, command.retryPolicy()));
        }
        return outcome;
    }

    /** How far behind delivery is — empty when nothing is pending. Feeds the pending-age gauge. */
    public Optional<Duration> oldestPendingAge() {
        return relayGateway.oldestPendingAge(clock.instant());
    }

    private RelayOutcome relay(OutboxEventRecord event, OutboxRetryPolicy policy) {
        return switch (sender.send(event)) {
            case OutboxSendResult.Sent sent -> publish(event);
            case OutboxSendResult.Unroutable unroutable -> deadLetter(event, unroutable.reason());
            case OutboxSendResult.Failed failed -> fail(event, failed.reason(), policy);
        };
    }

    private RelayOutcome publish(OutboxEventRecord event) {
        relayGateway.markPublished(event.eventId(), clock.instant());
        return RelayOutcome.publishedOne();
    }

    private RelayOutcome deadLetter(OutboxEventRecord event, String reason) {
        relayGateway.markDeadLettered(event.eventId(), reason);
        log.error(
                "Outbox event dead-lettered: eventId={} eventType={} reason={}",
                event.eventId(),
                event.eventType(),
                reason);
        return RelayOutcome.deadLetteredOne();
    }

    private RelayOutcome fail(OutboxEventRecord event, String reason, OutboxRetryPolicy policy) {
        OutboxFailure failure = new OutboxFailure(event.eventId(), reason, clock.instant());
        if (relayGateway.recordFailure(failure, policy)) {
            log.error(
                    "Outbox event dead-lettered after {} attempts: eventId={} eventType={} reason={}",
                    policy.maxAttempts(),
                    event.eventId(),
                    event.eventType(),
                    reason);
            return RelayOutcome.deadLetteredOne();
        }
        log.warn(
                "Outbox send failed, will retry: eventId={} eventType={} reason={}",
                event.eventId(),
                event.eventType(),
                reason);
        return RelayOutcome.failedOne();
    }
}
