package dev.nerviz.bankapp.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.AccountFixtures;
import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class AccountTest {

    private static final AccountId ID = AccountId.of(UUID.randomUUID());

    @Test
    void opensWithZeroBalanceAndActiveForActiveCustomer() {
        Customer customer = CustomerFixtures.activeCustomer();

        Account account = Account.open(ID, customer);

        assertThat(account.id()).isEqualTo(ID);
        assertThat(account.owner()).isEqualTo(customer.id());
        assertThat(account.balance().amount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @ParameterizedTest
    @EnumSource(
            value = CustomerStatus.class,
            names = {"KYC_IN_PROGRESS", "REJECTED_BY_KYC"})
    void rejectsIneligibleCustomer(CustomerStatus status) {
        Customer customer = CustomerFixtures.withStatus(status);

        assertThatThrownBy(() -> Account.open(ID, customer))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT");
    }

    @Test
    void rejectsAnonymizedCustomer() {
        Customer anonymized = CustomerFixtures.anonymized();

        assertThatThrownBy(() -> Account.open(ID, anonymized))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT");
    }

    @Test
    void rejectsMissingCustomer() {
        assertThatThrownBy(() -> Account.open(ID, null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_REQUIRED");
    }

    @Test
    void rejectsMissingId() {
        Customer customer = CustomerFixtures.activeCustomer();

        assertThatThrownBy(() -> Account.open(null, customer))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("ACCOUNT_ID_REQUIRED");
    }

    @Test
    void rejectsMissingOwner() {
        Money balance = Money.zero();

        assertThatThrownBy(() -> Account.rehydrate(ID, null, balance, AccountStatus.ACTIVE))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_ID_REQUIRED");
    }

    @Test
    void rejectsMissingBalance() {
        CustomerId owner = CustomerFixtures.activeCustomer().id();

        assertThatThrownBy(() -> Account.rehydrate(ID, owner, null, AccountStatus.ACTIVE))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("MONEY_REQUIRED");
    }

    @Test
    void rejectsMissingStatus() {
        CustomerId owner = CustomerFixtures.activeCustomer().id();
        Money balance = Money.zero();

        assertThatThrownBy(() -> Account.rehydrate(ID, owner, balance, null))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("ACCOUNT_STATUS_REQUIRED");
    }

    @Test
    void rehydrateRestoresPersistedState() {
        Customer customer = CustomerFixtures.activeCustomer();
        Account opened = AccountFixtures.openFor(customer);

        Account restored = Account.rehydrate(opened.id(), opened.owner(), opened.balance(), AccountStatus.INACTIVE);

        assertThat(restored.id()).isEqualTo(opened.id());
        assertThat(restored.status()).isEqualTo(AccountStatus.INACTIVE);
    }
}
