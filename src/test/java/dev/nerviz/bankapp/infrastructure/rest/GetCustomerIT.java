package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;

/**
 * The {@code Location} UC-001 returns resolves to the created customer only across the real
 * controller, use case and database — a slice with the use case doubled cannot prove it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnabledIf("dockerAvailable")
@Sql(
        statements = {"DELETE FROM customers", "DELETE FROM idempotency_keys"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class GetCustomerIT {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createdLocationResolvesToTheCustomer() throws Exception {
        String location = mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b80")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        assertThat(location).isNotNull();
        String createdId = location.substring(location.lastIndexOf('/') + 1);

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.securityNumber").value(CustomerFixtures.SECURITY_NUMBER))
                .andExpect(jsonPath("$.status").value("KYC_IN_PROGRESS"));
    }
}
