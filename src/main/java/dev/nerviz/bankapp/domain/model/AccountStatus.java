package dev.nerviz.bankapp.domain.model;

/**
 * Lifecycle of an account. A closed set with no formation rule of its own, so an enum and not a
 * value object ({@.claude/rules/value-objects.md}). Constant names are the persisted column
 * values: renaming one is a migration.
 *
 * <p>{@link #ACTIVE} is produced by {@link Account#open}. Nothing produces {@link #INACTIVE}
 * yet: the transition is {@code BL-08} (deactivate account), in the backlog.
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE;

    /** {@code true} only for {@link #ACTIVE}; the transfer asks it before moving money. */
    public boolean acceptsMovements() {
        return this == ACTIVE;
    }
}
