package dev.nerviz.bankapp.infrastructure.messaging.kycverificationcompleted;

import dev.nerviz.bankapp.application.usecase.customer.RecordKycVerdictCommand;
import dev.nerviz.bankapp.application.usecase.customer.RecordKycVerdictUseCase;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.KycVerdict;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Translates the KYC application's verdict into a call on {@link RecordKycVerdictUseCase}. The
 * offset commits only after that call returns ({@code .claude/rules/messaging.md} § Boundary).
 * Idempotent by state comparison — no dedupe table (25-messaging.md § 3).
 *
 * <p>{@code CUSTOMER_CONCURRENT_UPDATE} retries 3 times, 1 s / 2 s / 4 s, in process — the one
 * exception to "{@code DomainException} doesn't retry" (25-messaging.md § 4; see
 * {@code listener.KafkaListenerConfig}'s Javadoc for why it can't be classified at the
 * container's error handler instead). Every other {@code ConflictException} (the opposite
 * verdict already recorded), {@code CustomerNotFoundException} and a malformed verdict string
 * go straight to the DLQ, unretried.
 */
@Component
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class KycVerificationCompletedListener {

    private static final String CUSTOMER_CONCURRENT_UPDATE_ERROR_CODE = "CUSTOMER_CONCURRENT_UPDATE";
    private static final int MAX_CONCURRENT_UPDATE_RETRIES = 3;
    private static final long[] CONCURRENT_UPDATE_BACKOFF_MS = {1000L, 2000L, 4000L};

    RecordKycVerdictUseCase recordKycVerdict;

    @KafkaListener(topics = KycVerificationCompletedTopicConfig.TOPIC, groupId = "bank-app.kyc-verdict")
    void onMessage(KycVerificationCompletedPayload payload, Acknowledgment ack) {
        KycVerdict verdict = mapVerdict(payload.verdict());
        recordWithRetry(new RecordKycVerdictCommand(payload.customerId(), verdict));
        log.info("kyc verdict processed: customerId={} verdict={}", payload.customerId(), payload.verdict());
        ack.acknowledge();
    }

    private void recordWithRetry(RecordKycVerdictCommand command) {
        int attempt = 0;
        while (true) {
            try {
                recordKycVerdict.recordVerdict(command);
                return;
            } catch (ConflictException ex) {
                if (!isRetryable(ex, attempt)) {
                    throw ex;
                }
                backOffBeforeRetry(command, ex, attempt);
                attempt++;
            }
        }
    }

    private static void backOffBeforeRetry(RecordKycVerdictCommand command, ConflictException ex, int attempt) {
        long backoffMs = CONCURRENT_UPDATE_BACKOFF_MS[attempt];
        log.warn(
                "Kyc verdict concurrent update, retrying: customerId={} errorCode={} attempt={} backoffMs={}",
                command.customerId(),
                ex.errorCode(),
                attempt + 1,
                backoffMs);
        sleep(backoffMs);
    }

    private static boolean isRetryable(ConflictException ex, int attempt) {
        return CUSTOMER_CONCURRENT_UPDATE_ERROR_CODE.equals(ex.errorCode()) && attempt < MAX_CONCURRENT_UPDATE_RETRIES;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ConflictException(CUSTOMER_CONCURRENT_UPDATE_ERROR_CODE, "retry interrupted", interrupted);
        }
    }

    private static KycVerdict mapVerdict(String verdict) {
        if (verdict == null) {
            return null;
        }
        return switch (verdict) {
            case "APPROVED" -> KycVerdict.APPROVED;
            case "REJECT" -> KycVerdict.REJECT;
            default -> throw new ValidationException("KYC_VERDICT_UNKNOWN", "unknown kyc verdict: " + verdict);
        };
    }
}
