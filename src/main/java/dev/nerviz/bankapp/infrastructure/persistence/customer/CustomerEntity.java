package dev.nerviz.bankapp.infrastructure.persistence.customer;

import dev.nerviz.bankapp.infrastructure.persistence.shared.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Id assigned by the application: {@code Customer} is already born with a {@code CustomerId}.
 * {@code security_number} is {@code char(11)}, which a bare {@code String} does not infer —
 * {@code @JdbcTypeCode(SqlTypes.CHAR)} matches it ({@.claude/rules/persistence.md} § Mapping).
 */
@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
class CustomerEntity extends AssignedIdEntity<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "security_number", nullable = false, length = 11, updatable = false)
    String securityNumber;

    @Column(name = "birth_date", nullable = false, updatable = false)
    LocalDate birthDate;

    @Column(name = "registered_at", nullable = false, updatable = false)
    Instant registeredAt;

    @Version
    @Column(name = "version", nullable = false)
    long version;

    CustomerEntity(UUID id, String name, String securityNumber, LocalDate birthDate, Instant registeredAt) {
        this.id = id;
        this.name = name;
        this.securityNumber = securityNumber;
        this.birthDate = birthDate;
        this.registeredAt = registeredAt;
    }

    @Override
    public UUID getId() {
        return id;
    }
}
