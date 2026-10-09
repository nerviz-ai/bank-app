package dev.nerviz.bankapp.infrastructure.persistence.outbox;

import dev.nerviz.bankapp.infrastructure.persistence.shared.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The shared outbox row. {@code event_id} is assigned (UUID v7 by the appender), never
 * {@code @GeneratedValue}: it is the event's identity and the receiver's dedupe key.
 *
 * <p>No {@code @Version} and no mutator: every state change after the insert is a single
 * UPDATE in {@link OutboxEventJpaRepository}. {@code payload} holds personal data in clear
 * for as long as the row lives — the retention is the prune job's.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
class OutboxEventEntity extends AssignedIdEntity<UUID> {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    UUID eventId;

    @Column(name = "aggregate_id", nullable = false, length = 200, updatable = false)
    String aggregateId;

    @Column(name = "event_type", nullable = false, length = 120, updatable = false)
    String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, updatable = false)
    String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    Instant occurredAt;

    @Column(name = "published_at")
    Instant publishedAt;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "attempts", nullable = false)
    int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    Instant nextAttemptAt;

    @Column(name = "claimed_until")
    Instant claimedUntil;

    @Column(name = "last_error")
    String lastError;

    @Column(name = "dead_lettered", nullable = false)
    boolean deadLettered;

    OutboxEventEntity(UUID eventId, String aggregateId, String eventType, String payload, Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.nextAttemptAt = occurredAt;
    }

    @Override
    public UUID getId() {
        return eventId;
    }
}
