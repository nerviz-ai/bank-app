package dev.nerviz.bankapp.application.usecase.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.exception.NotFoundException;
import dev.nerviz.bankapp.domain.model.Customer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetCustomerUseCaseTest {

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final GetCustomerUseCase useCase = new GetCustomerUseCase(customerRepository);

    @Test
    void returnsCustomerWhenFound() {
        Customer customer = CustomerFixtures.customer();
        given(customerRepository.findById(customer.id())).willReturn(Optional.of(customer));

        Customer found = useCase.get(new GetCustomerCommand(customer.id().value()));

        assertThat(found).isEqualTo(customer);
    }

    @Test
    void rejectsUnknownId() {
        given(customerRepository.findById(any())).willReturn(Optional.empty());
        GetCustomerCommand command = new GetCustomerCommand(UUID.randomUUID());

        assertThatThrownBy(() -> useCase.get(command))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("customer not found")
                .extracting("errorCode")
                .isEqualTo("CUSTOMER_NOT_FOUND");
    }
}
