package dev.nerviz.bankapp.infrastructure.persistence.customer;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface CustomerJpaRepository extends JpaRepository<CustomerEntity, UUID> {

    boolean existsBySecurityNumber(String securityNumber);
}
