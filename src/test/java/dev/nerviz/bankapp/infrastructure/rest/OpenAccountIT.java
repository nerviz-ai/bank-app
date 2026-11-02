package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import dev.nerviz.bankapp.infrastructure.config.security.Roles;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;

/**
 * Access, ownership and replay meet only across the real filter chain, the aspect, the use case
 * and the database: a slice with the use case doubled proves none of them. The cleanup runs after
 * each test too, because other integration tests delete customers and an account row would stop
 * them on the foreign key.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnabledIf("dockerAvailable")
@Sql(
        statements = {"DELETE FROM accounts", "DELETE FROM customers", "DELETE FROM idempotency_keys"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(
        statements = {"DELETE FROM accounts", "DELETE FROM customers", "DELETE FROM idempotency_keys"},
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OpenAccountIT {

    private static final String KEY = "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b90";
    private static final String OTHER_SECURITY_NUMBER = "98765432109";

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    private static String accountsOf(UUID customerId) {
        return "/api/v1/customers/" + customerId + "/accounts";
    }

    private static RequestPostProcessor customerToken(String subject, UUID customerId) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.CUSTOMER))
                .jwt(token -> token.subject(subject).claim("customer_id", customerId.toString()));
    }

    private static RequestPostProcessor operatorToken(String subject) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.OPERATOR))
                .jwt(token -> token.subject(subject));
    }

    private static Stream<Arguments> tokenShapes() {
        Function<UUID, RequestPostProcessor> anonymous = id -> request -> request;
        Function<UUID, RequestPostProcessor> officerOnly =
                id -> jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.DATA_PROTECTION_OFFICER));
        Function<UUID, RequestPostProcessor> customerWithoutClaim =
                id -> jwt().authorities(new SimpleGrantedAuthority("ROLE_" + Roles.CUSTOMER));
        Function<UUID, RequestPostProcessor> ownCustomer = id -> customerToken("alice", id);
        Function<UUID, RequestPostProcessor> operator = id -> operatorToken("operator-1");
        return Stream.of(
                Arguments.of("anonymous", anonymous, 401),
                Arguments.of("data protection officer only", officerOnly, 403),
                Arguments.of("customer without customer_id claim", customerWithoutClaim, 403),
                Arguments.of("customer with its own customer_id", ownCustomer, 201),
                Arguments.of("operator", operator, 201));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int accountCountOf(Customer customer) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE customer_id = ?",
                Integer.class,
                customer.id().value());
        return count == null ? 0 : count;
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("tokenShapes")
    void answersByTokenShape(String shape, Function<UUID, RequestPostProcessor> token, int expectedStatus)
            throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.activeCustomer());

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(token.apply(customer.id().value())))
                .andExpect(status().is(expectedStatus));

        assertThat(accountCountOf(customer)).isEqualTo(expectedStatus == 201 ? 1 : 0);
    }

    @Test
    void createdAccountStartsActiveWithZeroBalance() throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.activeCustomer());

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(customerToken("alice", customer.id().value())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value(customer.id().value().toString()))
                .andExpect(jsonPath("$.balance").value(0.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void replayReturnsSameAccount() throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.activeCustomer());
        RequestPostProcessor token = customerToken("alice", customer.id().value());

        String firstLocation = mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(token))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(token))
                .andExpect(status().isCreated())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader("Location")).isEqualTo(firstLocation));

        assertThat(accountCountOf(customer)).isEqualTo(1);
    }

    @Test
    void distinctKeysOpenDistinctAccounts() throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.activeCustomer());
        RequestPostProcessor token = customerToken("alice", customer.id().value());

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(token))
                .andExpect(status().isCreated());
        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b91")
                        .with(token))
                .andExpect(status().isCreated());

        assertThat(accountCountOf(customer)).isEqualTo(2);
    }

    @Test
    void rejectsKeyReusedForAnotherCustomer() throws Exception {
        Customer first = customerRepository.save(CustomerFixtures.activeCustomer());
        Customer second = customerRepository.save(CustomerFixtures.activeCustomer(OTHER_SECURITY_NUMBER));
        mockMvc.perform(post(accountsOf(first.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(operatorToken("operator-1")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(accountsOf(second.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(operatorToken("operator-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(accountCountOf(second)).isZero();
    }

    @Test
    void storesKeyUnderThePrincipalName() throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.activeCustomer());

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(customerToken("alice", customer.id().value())))
                .andExpect(status().isCreated());

        String caller = jdbcTemplate.queryForObject(
                "SELECT caller_identity FROM idempotency_keys WHERE idempotency_key = ?::uuid", String.class, KEY);
        assertThat(caller).isEqualTo("alice");
    }

    @Test
    void anotherCallerNeverReplaysTheResponseOfAKey() throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.activeCustomer());
        String accountLocation = mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(customerToken("alice", customer.id().value())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        String accountId = accountLocation.substring(accountLocation.lastIndexOf('/') + 1);

        String body = mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(customerToken("mallory", UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain(accountId);
    }

    @Test
    void hidesAnotherCustomersExistenceAsNotFound() throws Exception {
        Customer owner = customerRepository.save(CustomerFixtures.activeCustomer());

        mockMvc.perform(post(accountsOf(owner.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(customerToken("mallory", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));

        assertThat(accountCountOf(owner)).isZero();
    }

    @Test
    void answersNotFoundForUnknownCustomerWhenOperator() throws Exception {
        mockMvc.perform(post(accountsOf(UUID.randomUUID()))
                        .header("Idempotency-Key", KEY)
                        .with(operatorToken("operator-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_FOUND"));
    }

    @Test
    void answersUnprocessableForCustomerStillInKyc() throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.withStatus(CustomerStatus.KYC_IN_PROGRESS));

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(customerToken("alice", customer.id().value())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT"));

        assertThat(accountCountOf(customer)).isZero();
    }

    @Test
    void releasesKeyWhenOpeningFails() throws Exception {
        Customer customer = customerRepository.save(CustomerFixtures.withStatus(CustomerStatus.REJECTED_BY_KYC));

        mockMvc.perform(post(accountsOf(customer.id().value()))
                        .header("Idempotency-Key", KEY)
                        .with(operatorToken("operator-1")))
                .andExpect(status().isUnprocessableEntity());

        Integer keys = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotency_keys WHERE idempotency_key = ?::uuid", Integer.class, KEY);
        assertThat(keys).isZero();
    }
}
