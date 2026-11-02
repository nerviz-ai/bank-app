package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nerviz.bankapp.AccountFixtures;
import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.application.usecase.account.OpenAccountCommand;
import dev.nerviz.bankapp.application.usecase.account.OpenAccountUseCase;
import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.CustomerNotFoundException;
import dev.nerviz.bankapp.domain.model.Account;
import dev.nerviz.bankapp.domain.model.Actor;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.infrastructure.config.security.JwtResourceServerConfig;
import dev.nerviz.bankapp.infrastructure.config.security.ProblemDetailAccessDeniedHandler;
import dev.nerviz.bankapp.infrastructure.config.security.ProblemDetailAuthenticationEntryPoint;
import dev.nerviz.bankapp.infrastructure.config.security.Roles;
import dev.nerviz.bankapp.infrastructure.config.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Web slice: HTTP contract only. Ownership and eligibility have their tests in the use case and
 * the aggregate; here a domain exception only has to come out as the right status and
 * {@code errorCode}. Replay and reused key run end to end in {@code OpenAccountIT}.
 */
@WebMvcTest(AccountController.class)
@Import({
    SecurityConfig.class,
    JwtResourceServerConfig.class,
    ProblemDetailAuthenticationEntryPoint.class,
    ProblemDetailAccessDeniedHandler.class,
    CurrentActorResolver.class
})
class AccountControllerTest {

    private static final String VALID_KEY = "0f2b6c1e-4d3a-4f5b-9c8d-7e6f5a4b3c2d";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenAccountUseCase openAccount;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private Tracer tracer;

    private static String accountsOf(UUID customerId) {
        return "/api/v1/customers/" + customerId + "/accounts";
    }

    private static RequestPostProcessor customerToken(UUID customerId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.CUSTOMER))
                .jwt(token -> token.claim("customer_id", customerId.toString()));
    }

    private static RequestPostProcessor operatorToken() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.OPERATOR));
    }

    @Test
    void opensAccountForOwnerReturningLocationAndBody() throws Exception {
        Customer customer = CustomerFixtures.activeCustomer();
        UUID customerId = customer.id().value();
        Account account = AccountFixtures.openFor(customer);
        given(openAccount.open(any())).willReturn(account);

        mockMvc.perform(post(accountsOf(customerId))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(customerToken(customerId)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                "Location", "/api/v1/accounts/" + account.id().value()))
                .andExpect(jsonPath("$.id").value(account.id().value().toString()))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        ArgumentCaptor<OpenAccountCommand> command = ArgumentCaptor.forClass(OpenAccountCommand.class);
        verify(openAccount).open(command.capture());
        assertThat(command.getValue().customerId()).isEqualTo(customerId);
        assertThat(command.getValue().actor()).isEqualTo(new Actor.Customer(CustomerId.of(customerId)));
    }

    @Test
    void opensAccountForOperatorWithOperatorActor() throws Exception {
        Customer customer = CustomerFixtures.activeCustomer();
        given(openAccount.open(any())).willReturn(AccountFixtures.openFor(customer));

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(operatorToken()))
                .andExpect(status().isCreated());
        ArgumentCaptor<OpenAccountCommand> command = ArgumentCaptor.forClass(OpenAccountCommand.class);
        verify(openAccount).open(command.capture());
        assertThat(command.getValue().actor()).isEqualTo(new Actor.Operator());
    }

    @Test
    void rejectsMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post(accountsOf(UUID.randomUUID())).with(operatorToken()))
                .andExpect(status().isBadRequest());

        verify(openAccount, never()).open(any());
    }

    @Test
    void rejectsMalformedCustomerId() throws Exception {
        mockMvc.perform(post("/api/v1/customers/abc/accounts")
                        .header("Idempotency-Key", VALID_KEY)
                        .with(operatorToken()))
                .andExpect(status().isBadRequest());

        verify(openAccount, never()).open(any());
    }

    @Test
    void answersNotFoundForUnknownCustomer() throws Exception {
        willThrow(new CustomerNotFoundException()).given(openAccount).open(any());

        mockMvc.perform(post(accountsOf(UUID.randomUUID()))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(operatorToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void answersNotFoundForAnotherCustomersId() throws Exception {
        willThrow(new CustomerNotFoundException()).given(openAccount).open(any());

        mockMvc.perform(post(accountsOf(UUID.randomUUID()))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(customerToken(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void answersUnprocessableForIneligibleCustomer() throws Exception {
        willThrow(new BusinessRuleViolationException(
                        "CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT", "customer is not eligible to hold an account"))
                .given(openAccount)
                .open(any());

        mockMvc.perform(post(accountsOf(UUID.randomUUID()))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(operatorToken()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT"));
    }

    @Test
    void hidesInternalFailureBehindTraceId() throws Exception {
        willThrow(new IllegalStateException("boom")).given(openAccount).open(any());

        mockMvc.perform(post(accountsOf(UUID.randomUUID()))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(operatorToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.detail").value("Internal error"));
    }

    @Test
    void deniesCustomerTokenWithoutCustomerIdClaim() throws Exception {
        mockMvc.perform(post(accountsOf(UUID.randomUUID()))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.CUSTOMER))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").doesNotExist());

        verify(openAccount, never()).open(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", ""})
    void deniesCustomerTokenWithMalformedCustomerIdClaim(String malformed) throws Exception {
        mockMvc.perform(post(accountsOf(UUID.randomUUID()))
                        .header("Idempotency-Key", VALID_KEY)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.CUSTOMER))
                                .jwt(token -> token.claim("customer_id", malformed))))
                .andExpect(status().isForbidden());

        verify(openAccount, never()).open(any());
    }
}
