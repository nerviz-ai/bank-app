package dev.nerviz.bankapp.infrastructure.persistence.idempotency;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, UUID> {

    /** Read by a future cleanup job (`BL-01`); not called on the request path. */
    List<IdempotencyKeyEntity> findByExpiresAtBefore(Instant cutoff);
}
