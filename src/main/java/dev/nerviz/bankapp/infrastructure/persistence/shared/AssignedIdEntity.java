package dev.nerviz.bankapp.infrastructure.persistence.shared;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * The {@code Persistable} mechanics every assigned-id entity needs, once per project.
 * {@code public} because every aggregate subpackage extends it ({@.claude/rules/persistence.md}
 * § Boundary) — the one consumer the visibility default allows for.
 */
@MappedSuperclass
public abstract class AssignedIdEntity<T> implements Persistable<T> {

    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
