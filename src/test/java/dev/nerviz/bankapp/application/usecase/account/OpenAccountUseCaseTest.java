package dev.nerviz.bankapp.application.usecase.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.port.AccountRepository;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Account;
import dev.nerviz.bankapp.domain.model.AccountStatus;
import dev.nerviz.bankapp.domain.model.Actor;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OpenAccountUseCaseTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final OpenAccountUseCase useCase = new OpenAccountUseCase(customerRepository, accountRepository);

    @Test
    void opensAndSavesAccountForOwner() {
        Customer customer = CustomerFixtures.activeCustomer();
        given(customerRepository.findById(customer.id())).willReturn(Optional.of(customer));
        given(accountRepository.save(any())).willAnswer(call -> call.getArgument(0));
        OpenAccountCommand command = new OpenAccountCommand(customer.id().value(), new Actor.Customer(customer.id()));

        Account account = useCase.open(command);

        assertThat(account.owner()).isEqualTo(customer.id());
        assertThat(account.balance().amount()).isEqualTo(new BigDecimal("0.00"));
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        verify(accountRepository).save(account);
    }

    @Test
    void generatesDistinctIdsForDistinctRequests() {
        Customer customer = CustomerFixtures.activeCustomer();
        given(customerRepository.findById(customer.id())).willReturn(Optional.of(customer));
        given(accountRepository.save(any())).willAnswer(call -> call.getArgument(0));
        OpenAccountCommand command = new OpenAccountCommand(customer.id().value(), new Actor.Operator());

        Account first = useCase.open(command);
        Account second = useCase.open(command);

        assertThat(first.id()).isNotEqualTo(second.id());
    }

    @Test
    void operatorOpensForAnyCustomer() {
        Customer customer = CustomerFixtures.activeCustomer();
        given(customerRepository.findById(customer.id())).willReturn(Optional.of(customer));
        given(accountRepository.save(any())).willAnswer(call -> call.getArgument(0));
        OpenAccountCommand command = new OpenAccountCommand(customer.id().value(), new Actor.Operator());

        Account account = useCase.open(command);

        assertThat(account.owner()).isEqualTo(customer.id());
        verify(accountRepository).save(account);
    }

    @Test
    void rejectsAbsentCustomerAsNotFound() {
        given(customerRepository.findById(any())).willReturn(Optional.empty());
        OpenAccountCommand command = new OpenAccountCommand(UUID.randomUUID(), new Actor.Operator());

        assertThatThrownBy(() -> useCase.open(command))
                .isInstanceOf(CustomerNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_FOUND");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void rejectsOtherCustomerActorAsNotFound() {
        Customer customer = CustomerFixtures.activeCustomer();
        given(customerRepository.findById(customer.id())).willReturn(Optional.of(customer));
        Actor other = new Actor.Customer(CustomerId.of(UUID.randomUUID()));
        OpenAccountCommand command = new OpenAccountCommand(customer.id().value(), other);

        assertThatThrownBy(() -> useCase.open(command))
                .isInstanceOf(CustomerNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_FOUND");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void propagatesIneligibility() {
        Customer customer = CustomerFixtures.withStatus(CustomerStatus.KYC_IN_PROGRESS);
        given(customerRepository.findById(customer.id())).willReturn(Optional.of(customer));
        OpenAccountCommand command = new OpenAccountCommand(customer.id().value(), new Actor.Operator());

        assertThatThrownBy(() -> useCase.open(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT");
        verify(accountRepository, never()).save(any());
    }
}
