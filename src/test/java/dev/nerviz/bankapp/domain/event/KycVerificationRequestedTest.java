package dev.nerviz.bankapp.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.SecurityNumber;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KycVerificationRequestedTest {

    private static final CustomerId ID = CustomerId.of(UUID.fromString("0198a000-0000-7000-8000-000000000001"));
    private static final SecurityNumber SECURITY_NUMBER = SecurityNumber.of("12345678901");
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 5, 17);
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void ofCopiesCustomerFieldsAndRegistrationInstant() {
        Customer customer = CustomerFixtures.customer();

        KycVerificationRequested event = KycVerificationRequested.of(customer);

        assertThat(event)
                .isEqualTo(new KycVerificationRequested(
                        customer.id(),
                        "Maria Silva",
                        SecurityNumber.of("12345678901"),
                        LocalDate.of(1990, 5, 17),
                        Instant.parse("2026-01-15T10:00:00Z")));
    }

    @ParameterizedTest
    @MethodSource("eventWithOneMissingField")
    void rejectsMissingField(
            CustomerId customerId,
            String name,
            SecurityNumber securityNumber,
            LocalDate birthDate,
            Instant occurredAt) {
        assertThatThrownBy(() -> new KycVerificationRequested(customerId, name, securityNumber, birthDate, occurredAt))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("KYC_REQUEST_FIELD_REQUIRED");
    }

    static Stream<Arguments> eventWithOneMissingField() {
        return Stream.of(
                Arguments.of(null, "Maria Silva", SECURITY_NUMBER, BIRTH_DATE, OCCURRED_AT),
                Arguments.of(ID, null, SECURITY_NUMBER, BIRTH_DATE, OCCURRED_AT),
                Arguments.of(ID, "Maria Silva", null, BIRTH_DATE, OCCURRED_AT),
                Arguments.of(ID, "Maria Silva", SECURITY_NUMBER, null, OCCURRED_AT),
                Arguments.of(ID, "Maria Silva", SECURITY_NUMBER, BIRTH_DATE, null));
    }

    @Test
    void toStringMasksSecurityNumberAndBirthDate() {
        KycVerificationRequested event =
                new KycVerificationRequested(ID, "Maria Silva", SECURITY_NUMBER, BIRTH_DATE, OCCURRED_AT);

        assertThat(event)
                .hasToString("KycVerificationRequested[customerId=0198a000-0000-7000-8000-000000000001,"
                        + " name=Maria Silva, securityNumber=*********01, birthDate=***,"
                        + " occurredAt=2026-01-15T10:00:00Z]");
    }
}
