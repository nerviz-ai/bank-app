package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;

/**
 * The aspect, the store and the transaction meet only in the full context. A contract
 * test with the use case doubled proves nothing about a replay. No fixed-clock override
 * here: every birth date used is either unambiguously adult (1990) or unambiguously
 * underage (2020), so the real system clock never changes the outcome.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnabledIf("dockerAvailable")
@Sql(
        statements = {"DELETE FROM customers", "DELETE FROM idempotency_keys"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CreateCustomerIdempotencyIT {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void replaysOriginalResponseForSameKey() throws Exception {
        String key = "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b7c";

        String firstLocation = mockMvc.perform(post("/api/v1/customers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");

        mockMvc.perform(post("/api/v1/customers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isCreated())
                .andExpect(result ->
                        assertThat(result.getResponse().getHeader("Location")).isEqualTo(firstLocation));

        Integer customerCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Integer.class);
        assertThat(customerCount).isEqualTo(1);
    }

    @Test
    void rejectsSameKeyWithDifferentBody() throws Exception {
        String key = "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b7d";
        mockMvc.perform(post("/api/v1/customers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/customers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Other Name","securityNumber":"10987654321","birthDate":"1985-03-20"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void releasesKeyWhenCreationFails() throws Exception {
        String key = "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b7e";

        mockMvc.perform(post("/api/v1/customers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Young Customer","securityNumber":"55566677788","birthDate":"2020-01-01"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        Integer idempotencyKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotency_keys WHERE idempotency_key = ?::uuid", Integer.class, key);
        assertThat(idempotencyKeyCount).isZero();
    }
}
