package dev.nerviz.bankapp.infrastructure.persistence.idempotency;

import dev.nerviz.bankapp.application.port.IdempotencyRequest;
import dev.nerviz.bankapp.infrastructure.persistence.shared.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The key IS the primary key: global uniqueness is exactly the guarantee wanted, and it
 * is what makes the racing INSERT in {@code IdempotencyKeyStore.claim} the concurrency
 * control.
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
class IdempotencyKeyEntity extends AssignedIdEntity<UUID> {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    UUID idempotencyKey;

    @Column(name = "route", nullable = false, length = 200, updatable = false)
    String route;

    @Column(name = "caller_identity", nullable = false, length = 200, updatable = false)
    String callerIdentity;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "body_hash", nullable = false, length = 64, updatable = false)
    String bodyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    IdempotencyStatus status;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "response_status")
    Integer responseStatus;

    @Column(name = "response_body")
    String responseBody;

    @Column(name = "response_headers")
    String responseHeaders;

    @Column(name = "claimed_at", nullable = false)
    Instant claimedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    IdempotencyKeyEntity(IdempotencyRequest request, Instant expiresAt) {
        this.idempotencyKey = request.key();
        this.route = request.route();
        this.callerIdentity = request.callerIdentity();
        this.bodyHash = request.bodyHash();
        this.status = IdempotencyStatus.IN_PROGRESS;
        this.claimedAt = request.now();
        this.createdAt = request.now();
        this.expiresAt = expiresAt;
    }

    void applyResponse(int responseStatus, String responseBody, String responseHeaders) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.responseHeaders = responseHeaders;
    }

    void reclaim(Instant now) {
        this.claimedAt = now;
    }

    @Override
    public UUID getId() {
        return idempotencyKey;
    }
}
