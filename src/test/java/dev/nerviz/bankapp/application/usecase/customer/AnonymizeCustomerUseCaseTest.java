package dev.nerviz.bankapp.application.usecase.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerIdentity;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Orchestration only: the aggregate's rules have their test in {@code CustomerTest}. */
class AnonymizeCustomerUseCaseTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AnonymizeCustomerUseCase useCase =
            new AnonymizeCustomerUseCase(customerRepository, CustomerFixtures.FIXED_CLOCK);

    @Test
    void anonymizesAndSavesActiveCustomer() {
        Customer active = CustomerFixtures.withStatus(CustomerStatus.ACTIVE);
        given(customerRepository.findById(active.id())).willReturn(Optional.of(active));
        given(customerRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        useCase.anonymize(new AnonymizeCustomerCommand(active.id().value()));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo(Customer.ANONYMIZED_NAME);
        assertThat(saved.getValue().identity()).isInstanceOf(CustomerIdentity.Anonymized.class);
        assertThat(saved.getValue().status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void doesNotSaveWhenAlreadyAnonymized() {
        Customer anonymized = CustomerFixtures.anonymized();
        given(customerRepository.findById(anonymized.id())).willReturn(Optional.of(anonymized));

        useCase.anonymize(new AnonymizeCustomerCommand(anonymized.id().value()));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundForUnknownId() {
        given(customerRepository.findById(any())).willReturn(Optional.empty());
        AnonymizeCustomerCommand command = new AnonymizeCustomerCommand(UUID.randomUUID());

        assertThatThrownBy(() -> useCase.anonymize(command))
                .isInstanceOf(CustomerNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_FOUND");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void propagatesKycInProgressRefusal() {
        Customer inProgress = CustomerFixtures.customer();
        given(customerRepository.findById(inProgress.id())).willReturn(Optional.of(inProgress));
        AnonymizeCustomerCommand command =
                new AnonymizeCustomerCommand(inProgress.id().value());

        assertThatThrownBy(() -> useCase.anonymize(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_KYC_IN_PROGRESS");

        verify(customerRepository, never()).save(any());
    }
}
