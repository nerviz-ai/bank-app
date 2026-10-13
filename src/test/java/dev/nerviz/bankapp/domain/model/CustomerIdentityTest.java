package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CustomerIdentityTest {

    private static final SecurityNumber SECURITY_NUMBER = SecurityNumber.of("12345678901");
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 17);

    @ParameterizedTest
    @MethodSource("identifiedWithOneMissingValue")
    void identifiedRequiresBothValues(SecurityNumber securityNumber, LocalDate birthDate, String expectedCode) {
        assertThatThrownBy(() -> CustomerIdentity.identified(securityNumber, birthDate))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(expectedCode);
    }

    private static Stream<Arguments> identifiedWithOneMissingValue() {
        return Stream.of(
                Arguments.of(null, BIRTH_DATE, "SECURITY_NUMBER_REQUIRED"),
                Arguments.of(SECURITY_NUMBER, null, "BIRTH_DATE_REQUIRED"));
    }

    @Test
    void anonymizedRequiresInstant() {
        assertThatThrownBy(() -> CustomerIdentity.anonymized(null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("ANONYMIZED_AT_REQUIRED");
    }

    @Test
    void anonymizedKeepsTheInstant() {
        Instant at = Instant.parse("2026-01-15T10:00:00Z");

        CustomerIdentity identity = CustomerIdentity.anonymized(at);

        assertThat(identity).isEqualTo(new CustomerIdentity.Anonymized(at));
    }

    @Test
    void identifiedToStringMasksBothValues() {
        CustomerIdentity identity = CustomerIdentity.identified(SECURITY_NUMBER, BIRTH_DATE);

        assertThat(identity.toString()).doesNotContain("12345678901").doesNotContain("1990");
    }
}
