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
import dev.nerviz.bankapp.domain.exception.SecurityNumberAlreadyRegisteredException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Orchestration, not business rules: that the use case calls the right ports in order
 * and propagates what the domain rejects. The aggregate's invariants have their own test
 * at the domain level.
 */
class CreateCustomerUseCaseTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final CreateCustomerUseCase useCase =
            new CreateCustomerUseCase(customerRepository, CustomerFixtures.FIXED_CLOCK);

    @Test
    void createsCustomerAndReturnsItsId() {
        given(customerRepository.existsBySecurityNumber(any())).willReturn(false);
        given(customerRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        CustomerId id = useCase.create(CustomerFixtures.command());

        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(captor.capture());
        Customer saved = captor.getValue();
        assertThat(saved.name()).isEqualTo(CustomerFixtures.NAME);
        assertThat(saved.securityNumber().value()).isEqualTo(CustomerFixtures.SECURITY_NUMBER);
        assertThat(id).isEqualTo(saved.id());
    }

    @Test
    void rejectsAlreadyRegisteredSecurityNumber() {
        given(customerRepository.existsBySecurityNumber(any())).willReturn(true);
        CreateCustomerCommand command = CustomerFixtures.command();

        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(SecurityNumberAlreadyRegisteredException.class)
                .extracting("errorCode")
                .isEqualTo("SECURITY_NUMBER_ALREADY_REGISTERED");

        verify(customerRepository, never()).save(any());
    }

    @Test
    void rejectsMalformedSecurityNumberBeforeLookup() {
        CreateCustomerCommand command =
                new CreateCustomerCommand(CustomerFixtures.NAME, "123", CustomerFixtures.BIRTH_DATE);

        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo("SECURITY_NUMBER_INVALID");

        verify(customerRepository, never()).existsBySecurityNumber(any());
    }

    @Test
    void rejectsUnderageWithoutSaving() {
        given(customerRepository.existsBySecurityNumber(any())).willReturn(false);
        CreateCustomerCommand command = new CreateCustomerCommand(
                CustomerFixtures.NAME, CustomerFixtures.SECURITY_NUMBER, LocalDate.of(2010, 6, 1));

        assertThatThrownBy(() -> useCase.create(command))
                .isInstanceOf(BusinessRuleViolationException.class)
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_UNDERAGE");

        verify(customerRepository, never()).save(any());
    }
}
