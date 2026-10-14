package dev.nerviz.bankapp.infrastructure.messaging.kycverificationcompleted;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.nerviz.bankapp.application.usecase.customer.RecordKycVerdictCommand;
import dev.nerviz.bankapp.application.usecase.customer.RecordKycVerdictUseCase;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.KycVerdict;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Adapter translation only — no broker needed; the use case is a double. */
class KycVerificationCompletedListenerTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private final RecordKycVerdictUseCase recordKycVerdict = mock(RecordKycVerdictUseCase.class);
    private final KycVerificationCompletedListener listener = new KycVerificationCompletedListener(recordKycVerdict);

    @ParameterizedTest
    @MethodSource("wireVerdictsAndTheirDomainCounterpart")
    void mapsPayloadToTheUseCase(String wireVerdict, KycVerdict verdict) {
        KycVerificationCompletedPayload payload =
                new KycVerificationCompletedPayload(EVENT_ID, CUSTOMER_ID, wireVerdict);

        listener.onMessage(payload);

        verify(recordKycVerdict).recordVerdict(eq(new RecordKycVerdictCommand(CUSTOMER_ID, verdict)));
    }

    private static Stream<Arguments> wireVerdictsAndTheirDomainCounterpart() {
        return Stream.of(Arguments.of("APPROVED", KycVerdict.APPROVED), Arguments.of("REJECT", KycVerdict.REJECT));
    }

    @ParameterizedTest
    @MethodSource("unknownVerdicts")
    void rejectsUnknownVerdict(String unknownVerdict) {
        KycVerificationCompletedPayload payload =
                new KycVerificationCompletedPayload(EVENT_ID, CUSTOMER_ID, unknownVerdict);

        assertThatThrownBy(() -> listener.onMessage(payload))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("KYC_VERDICT_UNKNOWN");

        verifyNoInteractions(recordKycVerdict);
    }

    private static Stream<Arguments> unknownVerdicts() {
        return Stream.of(Arguments.of("approved"), Arguments.of("REJECTED"), Arguments.of(""));
    }

    @Test
    void propagatesUseCaseFailureToTheContainer() {
        KycVerificationCompletedPayload payload =
                new KycVerificationCompletedPayload(EVENT_ID, CUSTOMER_ID, "APPROVED");
        willThrow(new CustomerNotFoundException()).given(recordKycVerdict).recordVerdict(any());

        assertThatThrownBy(() -> listener.onMessage(payload)).isInstanceOf(CustomerNotFoundException.class);
    }
}
