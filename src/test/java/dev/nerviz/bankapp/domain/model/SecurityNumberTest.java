package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityNumberTest {

    @Test
    void acceptsElevenDigits() {
        SecurityNumber securityNumber = SecurityNumber.of("12345678901");

        assertThat(securityNumber.value()).isEqualTo("12345678901");
    }

    @Test
    void trimsSurroundingWhitespace() {
        SecurityNumber securityNumber = SecurityNumber.of(" 12345678901 ");

        assertThat(securityNumber.value()).isEqualTo("12345678901");
    }

    @Test
    void rejectsMissingValue() {
        assertThatThrownBy(() -> SecurityNumber.of(null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("SECURITY_NUMBER_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "123456789012", "123.456.789-01", "1234567890a", "", "   "})
    void rejectsMalformedValue(String malformed) {
        assertThatThrownBy(() -> SecurityNumber.of(malformed))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("SECURITY_NUMBER_INVALID");
    }

    @Test
    void masksAllButLastTwoDigits() {
        SecurityNumber securityNumber = SecurityNumber.of("12345678901");

        assertThat(securityNumber.masked()).isEqualTo("*********01");
    }

    @Test
    void toStringIsMasked() {
        SecurityNumber securityNumber = SecurityNumber.of("12345678901");

        assertThat(securityNumber).hasToString("*********01");
    }
}
