package dev.nerviz.bankapp.infrastructure.persistence.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {}
