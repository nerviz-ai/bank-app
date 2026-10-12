package dev.nerviz.bankapp.domain.model;

/**
 * Outcome of a KYC verification. A closed set with no formation rule of its own, so an enum
 * and not a value object ({@.claude/rules/value-objects.md}). Constant names are the wire
 * values of the verdict contract: renaming one breaks the publisher.
 */
public enum KycVerdict {
    APPROVED(CustomerStatus.ACTIVE),
    REJECT(CustomerStatus.REJECTED_BY_KYC);

    private final CustomerStatus resultingStatus;

    KycVerdict(CustomerStatus resultingStatus) {
        this.resultingStatus = resultingStatus;
    }

    /** The status a customer in {@link CustomerStatus#KYC_IN_PROGRESS} reaches with this verdict. */
    public CustomerStatus resultingStatus() {
        return resultingStatus;
    }
}
