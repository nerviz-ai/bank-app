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
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import dev.nerviz.bankapp.domain.model.KycVerdict;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Orchestration, not business rules: the aggregate's invariants have their own test in
 * {@code CustomerTest}.
 */
class RecordKycVerdictUseCaseTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final RecordKycVerdictUseCase useCase = new RecordKycVerdictUseCase(customerRepository);

    @Test
    void approvesCustomerInProgress() {
        Customer inProgress = CustomerFixtures.customer();
        given(customerRepository.findById(inProgress.id())).willReturn(Optional.of(inProgress));
        given(customerRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        useCase.recordVerdict(new RecordKycVerdictCommand(inProgress.id().value(), KycVerdict.APPROVED));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(CustomerStatus.ACTIVE);
    }

    @Test
    void rejectsCustomerInProgress() {
        Customer inProgress = CustomerFixtures.customer();
        given(customerRepository.findById(inProgress.id())).willReturn(Optional.of(inProgress));
        given(customerRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        useCase.recordVerdict(new RecordKycVerdictCommand(inProgress.id().value(), KycVerdict.REJECT));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(CustomerStatus.REJECTED_BY_KYC);
    }

    @Test
    void duplicateVerdictWritesNothing() {
        Customer active = CustomerFixtures.withStatus(CustomerStatus.ACTIVE);
        given(customerRepository.findById(active.id())).willReturn(Optional.of(active));

        useCase.recordVerdict(new RecordKycVerdictCommand(active.id().value(), KycVerdict.APPROVED));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void conflictingVerdictWritesNothing() {
        Customer active = CustomerFixtures.withStatus(CustomerStatus.ACTIVE);
        given(customerRepository.findById(active.id())).willReturn(Optional.of(active));
        RecordKycVerdictCommand command =
                new RecordKycVerdictCommand(active.id().value(), KycVerdict.REJECT);

        assertThatThrownBy(() -> useCase.recordVerdict(command))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo("KYC_VERDICT_CONFLICT");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void unknownCustomerIsNotFound() {
        given(customerRepository.findById(any())).willReturn(Optional.empty());
        RecordKycVerdictCommand command = new RecordKycVerdictCommand(UUID.randomUUID(), KycVerdict.APPROVED);

        assertThatThrownBy(() -> useCase.recordVerdict(command))
                .isInstanceOf(CustomerNotFoundException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_FOUND");

        verify(customerRepository, never()).save(any());
    }
}
