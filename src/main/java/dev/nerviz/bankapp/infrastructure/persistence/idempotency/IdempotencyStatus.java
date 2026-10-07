package dev.nerviz.bankapp.infrastructure.persistence.idempotency;

/** Persistence-side enum. STRING storage — {@.claude/rules/persistence.md} § Mapping. */
enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
