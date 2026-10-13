package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;

/**
 * The only place the chain, the controller, the use case, the adapter and the migration meet.
 * Asserts just the two paths that cross every layer; rules of the aggregate have their own test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnabledIf("dockerAvailable")
@Sql(
        statements = {"DELETE FROM customers", "DELETE FROM idempotency_keys", "DELETE FROM outbox_events"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AnonymizeCustomerIT {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static RequestPostProcessor officer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_DATA_PROTECTION_OFFICER"));
    }

    @Test
    void officerAnonymizesActiveCustomerAndReadShowsNoPersonalData() throws Exception {
        Customer active = customerRepository.save(CustomerFixtures.withStatus(CustomerStatus.ACTIVE));
        String path = CustomerController.BASE_PATH + "/" + active.id().value();

        mockMvc.perform(delete(path).with(officer()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        Map<String, Object> firstRow = jdbcTemplate.queryForMap(
                "SELECT name, security_number, birth_date, anonymized_at FROM customers WHERE id = ?",
                active.id().value());
        mockMvc.perform(delete(path).with(officer())).andExpect(status().isNoContent());

        assertThat(firstRow)
                .containsEntry("name", Customer.ANONYMIZED_NAME)
                .containsEntry("security_number", null)
                .containsEntry("birth_date", null);
        assertThat(firstRow.get("anonymized_at")).isNotNull();
        assertThat(jdbcTemplate.queryForMap(
                        "SELECT anonymized_at FROM customers WHERE id = ?",
                        active.id().value()))
                .isEqualTo(Map.of("anonymized_at", firstRow.get("anonymized_at")));
        mockMvc.perform(get(path).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(Customer.ANONYMIZED_NAME))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.securityNumber").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist());
    }

    @Test
    void freshCustomerIsRefusedWhileKycInProgress() throws Exception {
        String location = mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6ba0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        assertThat(location).isNotNull();

        mockMvc.perform(delete(location).with(officer()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CUSTOMER_KYC_IN_PROGRESS"));
    }

    @Test
    void unknownIdIsAnsweredAsSuccess() throws Exception {
        mockMvc.perform(delete(CustomerController.BASE_PATH + "/00000000-0000-7000-8000-000000000000")
                        .with(officer()))
                .andExpect(status().isNoContent());
    }
}
