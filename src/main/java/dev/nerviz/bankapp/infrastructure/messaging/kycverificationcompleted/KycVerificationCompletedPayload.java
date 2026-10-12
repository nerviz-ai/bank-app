package dev.nerviz.bankapp.infrastructure.messaging.kycverificationcompleted;

import java.util.UUID;

/**
 * The wire shape the KYC application publishes (25-mensageria.md § 2), converted into the
 * listener's typed parameter by the shared {@code StringJacksonJsonMessageConverter}.
 * {@code verdict} stays a raw {@code String}: the listener maps it into
 * {@link dev.nerviz.bankapp.domain.model.KycVerdict}, and an unmapped value is the listener's
 * own {@code ValidationException}, not a defect in this record. No personal data (25-mensageria.md
 * § 8): both ids are internal surrogates and the verdict is a two-value outcome.
 */
record KycVerificationCompletedPayload(UUID eventId, UUID customerId, String verdict) {}
