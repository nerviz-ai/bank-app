package dev.nerviz.bankapp.infrastructure.messaging.kycverificationcompleted;

import dev.nerviz.bankapp.application.usecase.customer.RecordKycVerdictCommand;
import dev.nerviz.bankapp.application.usecase.customer.RecordKycVerdictUseCase;
import dev.nerviz.bankapp.commons.logging.annotations.LogExecution;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.KycVerdict;
import io.github.springwolf.bindings.kafka.annotations.KafkaAsyncOperationBinding;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Translates the KYC application's verdict into a call on {@link RecordKycVerdictUseCase}. The
 * container commits the offset after this method returns ({@code .claude/rules/messaging.md}
 * § Configuration). Idempotent by state comparison — no dedupe table (25-messaging.md § 3).
 *
 * <p>No retry here: the container's error handler classifies the failure by type
 * ({@code listener.ListenerFailureClassification}) — {@code ConcurrentUpdateException} retries
 * 1 s / 2 s / 4 s; every other {@code ConflictException} (the opposite verdict already recorded),
 * {@code CustomerNotFoundException} and a malformed verdict string go straight to the DLQ.
 */
@Component
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class KycVerificationCompletedListener {

    private static final String GROUP_ID = "bank-app.kyc-verdict";

    RecordKycVerdictUseCase recordKycVerdict;

    @LogExecution
    @KafkaListener(topics = KycVerificationCompletedTopicConfig.TOPIC, groupId = GROUP_ID)
    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = KycVerificationCompletedTopicConfig.TOPIC,
                            description = "Receives the KYC application's verdict (APPROVED or REJECT) for a"
                                    + " customer and records it on the customer; idempotent. Unknown verdicts and"
                                    + " conflicts go to <topic>.dlq.",
                            payloadType = KycVerificationCompletedPayload.class,
                            message =
                                    @AsyncMessage(
                                            messageId = "KycVerificationCompleted",
                                            name = "KycVerificationCompleted"),
                            headers = @AsyncOperation.Headers(notUsed = true)))
    @KafkaAsyncOperationBinding(groupId = GROUP_ID)
    void onMessage(KycVerificationCompletedPayload payload) {
        KycVerdict verdict = mapVerdict(payload.verdict());
        recordKycVerdict.recordVerdict(new RecordKycVerdictCommand(payload.customerId(), verdict));
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
