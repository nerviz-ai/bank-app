package dev.nerviz.bankapp.domain.model;

/**
 * Where a customer stands in the KYC verification. A closed set with no formation rule of its
 * own, so an enum and not a value object ({@.claude/rules/value-objects.md}). Constant names
 * are the persisted column values: renaming one is a migration.
 *
 * <p>{@code register} is the only producer of {@link #KYC_IN_PROGRESS}; the only way out of it
 * is {@link Customer#recordKycVerdict(KycVerdict)}, and {@link #ACTIVE} and
 * {@link #REJECTED_BY_KYC} are final.
 */
public enum CustomerStatus {
    KYC_IN_PROGRESS,
    ACTIVE,
    REJECTED_BY_KYC
}
