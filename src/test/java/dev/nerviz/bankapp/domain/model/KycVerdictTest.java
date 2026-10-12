package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KycVerdictTest {

    @ParameterizedTest
    @MethodSource("verdictsAndTheirResultingStatus")
    void mapsToResultingStatus(KycVerdict verdict, CustomerStatus expectedStatus) {
        assertThat(verdict.resultingStatus()).isEqualTo(expectedStatus);
    }

    private static Stream<Arguments> verdictsAndTheirResultingStatus() {
        return Stream.of(
                Arguments.of(KycVerdict.APPROVED, CustomerStatus.ACTIVE),
                Arguments.of(KycVerdict.REJECT, CustomerStatus.REJECTED_BY_KYC));
    }
}
