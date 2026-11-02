package dev.nerviz.bankapp.infrastructure.persistence.account;

import dev.nerviz.bankapp.domain.model.AccountStatus;
import dev.nerviz.bankapp.infrastructure.persistence.shared.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Id assigned by the application: {@code Account} is born with an {@code AccountId}. The owner is
 * kept by id, with no association — the aggregates reference each other by id.
 * {@code opened_at} and {@code version} exist only here: the domain has no such fields.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
class AccountEntity extends AssignedIdEntity<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    UUID customerId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    AccountStatus status;

    @Column(name = "opened_at", nullable = false, updatable = false)
    Instant openedAt;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    AccountEntity(UUID id, UUID customerId, BigDecimal balance, AccountStatus status, Instant openedAt) {
        this.id = id;
        this.customerId = customerId;
        this.balance = balance;
        this.status = status;
        this.openedAt = openedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
