package dev.nerviz.bankapp.application.port;

import dev.nerviz.bankapp.domain.event.KycVerificationRequested;

/**
 * Output port, kind messaging. {@link #request} <b>records</b> the request inside the caller's
 * transaction — it does not send. A committed transaction guarantees the request will be
 * delivered; a rolled-back one guarantees it never will. Any failure to record propagates and
 * rolls the caller back; no broker, Spring, JPA or Jackson type crosses this signature.
 */
public interface RequestKycVerification {

    void request(KycVerificationRequested event);
}
