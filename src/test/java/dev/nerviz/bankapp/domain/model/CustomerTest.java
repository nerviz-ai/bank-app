package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CustomerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final CustomerId ID = CustomerId.of(UUID.randomUUID());
    private static final SecurityNumber SECURITY_NUMBER = SecurityNumber.of("12345678901");
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 5, 17);

    @Test
    void registersWithTrimmedNameAndClockInstant() {
        Customer customer = Customer.register(ID, "  Maria Silva  ", SECURITY_NUMBER, VALID_BIRTH_DATE, CLOCK);

        assertThat(customer.name()).isEqualTo("Maria Silva");
        assertThat(customer.registeredAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void rejectsMissingId() {
        assertThatThrownBy(() -> Customer.register(null, "Maria Silva", SECURITY_NUMBER, VALID_BIRTH_DATE, CLOCK))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_ID_REQUIRED");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankName(String blank) {
        assertThatThrownBy(() -> Customer.register(ID, blank, SECURITY_NUMBER, VALID_BIRTH_DATE, CLOCK))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NAME_REQUIRED");
    }

    @ParameterizedTest
    @MethodSource("outOfLengthNames")
    void rejectsNameOutsideLength(String outOfLength) {
        assertThatThrownBy(() -> Customer.register(ID, outOfLength, SECURITY_NUMBER, VALID_BIRTH_DATE, CLOCK))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NAME_LENGTH");
    }

    private static Stream<Arguments> outOfLengthNames() {
        return Stream.of(Arguments.of("A"), Arguments.of(" A "), Arguments.of("a".repeat(121)));
    }

    @ParameterizedTest
    @MethodSource("boundaryNames")
    void acceptsNameAtBounds(String atBound) {
        Customer customer = Customer.register(ID, atBound, SECURITY_NUMBER, VALID_BIRTH_DATE, CLOCK);

        assertThat(customer.name()).hasSize(atBound.length());
    }

    private static Stream<Arguments> boundaryNames() {
        return Stream.of(Arguments.of("a".repeat(2)), Arguments.of("a".repeat(120)));
    }

    @Test
    void rejectsMissingSecurityNumber() {
        assertThatThrownBy(() -> Customer.register(ID, "Maria Silva", null, VALID_BIRTH_DATE, CLOCK))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("SECURITY_NUMBER_REQUIRED");
    }

    @Test
    void rejectsMissingBirthDate() {
        assertThatThrownBy(() -> Customer.register(ID, "Maria Silva", SECURITY_NUMBER, null, CLOCK))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("BIRTH_DATE_REQUIRED");
    }

    @Test
    void rejectsFutureBirthDate() {
        LocalDate tomorrow = LocalDate.of(2026, 1, 16);

        assertThatThrownBy(() -> Customer.register(ID, "Maria Silva", SECURITY_NUMBER, tomorrow, CLOCK))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("BIRTH_DATE_IN_FUTURE");
    }

    @Test
    void rejectsAgeOver130() {
        LocalDate the131stBirthdayToday = LocalDate.of(1895, 1, 15);

        assertThatThrownBy(() -> Customer.register(ID, "Maria Silva", SECURITY_NUMBER, the131stBirthdayToday, CLOCK))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("BIRTH_DATE_TOO_OLD");
    }

    @Test
    void acceptsAge130() {
        LocalDate the130thBirthdayToday = LocalDate.of(1895, 1, 16);

        Customer customer = Customer.register(ID, "Maria Silva", SECURITY_NUMBER, the130thBirthdayToday, CLOCK);

        assertThat(customer.birthDate()).isEqualTo(the130thBirthdayToday);
    }

    @ParameterizedTest
    @MethodSource("underageBirthDates")
    void rejectsCustomerNotStrictlyOver18(LocalDate underageBirthDate) {
        assertThatThrownBy(() -> Customer.register(ID, "Maria Silva", SECURITY_NUMBER, underageBirthDate, CLOCK))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_UNDERAGE");
    }

    private static Stream<Arguments> underageBirthDates() {
        return Stream.of(
                Arguments.of(LocalDate.of(2008, 1, 15)),
                Arguments.of(LocalDate.of(2008, 1, 16)),
                Arguments.of(LocalDate.of(2010, 6, 1)));
    }

    @Test
    void accepts18thBirthdayYesterday() {
        LocalDate the18thBirthdayYesterday = LocalDate.of(2008, 1, 14);

        Customer customer = Customer.register(ID, "Maria Silva", SECURITY_NUMBER, the18thBirthdayYesterday, CLOCK);

        assertThat(customer.birthDate()).isEqualTo(the18thBirthdayYesterday);
    }

    @Test
    void appliesFebruary28AnniversaryForLeapDayBirthRejected() {
        Clock beforeAnniversary = Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), ZoneOffset.UTC);
        LocalDate leapDayBirth = LocalDate.of(2008, 2, 29);

        assertThatThrownBy(() -> Customer.register(ID, "Maria Silva", SECURITY_NUMBER, leapDayBirth, beforeAnniversary))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_UNDERAGE");
    }

    @Test
    void appliesFebruary28AnniversaryForLeapDayBirthAccepted() {
        Clock onAnniversary = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);
        LocalDate leapDayBirth = LocalDate.of(2008, 2, 29);

        Customer customer = Customer.register(ID, "Maria Silva", SECURITY_NUMBER, leapDayBirth, onAnniversary);

        assertThat(customer.birthDate()).isEqualTo(leapDayBirth);
    }

    @Test
    void rehydrateDoesNotReapplyDateRules() {
        LocalDate futureIfRulesReran = LocalDate.of(2015, 3, 10);

        Customer customer = Customer.rehydrate(
                ID, "Maria Silva", SECURITY_NUMBER, futureIfRulesReran, CLOCK.instant(), CustomerStatus.ACTIVE);

        assertThat(customer.birthDate()).isEqualTo(futureIfRulesReran);
    }

    @Test
    void rejectsMissingRegisteredAtOnRehydrate() {
        assertThatThrownBy(() -> Customer.rehydrate(
                        ID, "Maria Silva", SECURITY_NUMBER, VALID_BIRTH_DATE, null, CustomerStatus.ACTIVE))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("REGISTERED_AT_REQUIRED");
    }

    @Test
    void registerBornKycInProgress() {
        Customer customer = Customer.register(ID, "Maria Silva", SECURITY_NUMBER, VALID_BIRTH_DATE, CLOCK);

        assertThat(customer.status()).isEqualTo(CustomerStatus.KYC_IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(CustomerStatus.class)
    void rehydrateRestoresStatus(CustomerStatus persisted) {
        Customer customer =
                Customer.rehydrate(ID, "Maria Silva", SECURITY_NUMBER, VALID_BIRTH_DATE, CLOCK.instant(), persisted);

        assertThat(customer.status()).isEqualTo(persisted);
    }

    @Test
    void rejectsMissingStatus() {
        Instant registeredAt = CLOCK.instant();

        assertThatThrownBy(() ->
                        Customer.rehydrate(ID, "Maria Silva", SECURITY_NUMBER, VALID_BIRTH_DATE, registeredAt, null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_STATUS_REQUIRED");
    }
}
