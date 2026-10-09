package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
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
 * Invariant 4 of {@code 10-dominio.md}: one request per committed creation, none otherwise.
 * Only the real transaction across {@code customers} and {@code outbox_events} can prove it —
 * a use case test with the port doubled cannot see the rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnabledIf("dockerAvailable")
@Sql(
        statements = {"DELETE FROM customers", "DELETE FROM idempotency_keys", "DELETE FROM outbox_events"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class CreateCustomerOutboxIT {

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void creationRecordsOneOutboxRowWithPayload() throws Exception {
        String location = mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b90")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        assertThat(location).isNotNull();
        String customerId = location.substring(location.lastIndexOf('/') + 1);

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList("SELECT event_id::text AS event_id, aggregate_id, event_type, published_at,"
                        + " payload->>'eventId' AS payload_event_id, payload->>'customerId' AS customer_id,"
                        + " payload->>'name' AS name, payload->>'securityNumber' AS security_number,"
                        + " payload->>'birthDate' AS birth_date, payload->>'occurredAt' AS occurred_at"
                        + " FROM outbox_events");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0))
                .containsEntry("aggregate_id", customerId)
                .containsEntry("event_type", "KycVerificationRequested")
                .containsEntry("customer_id", customerId)
                .containsEntry("name", CustomerFixtures.NAME)
                .containsEntry("security_number", CustomerFixtures.SECURITY_NUMBER)
                .containsEntry("birth_date", CustomerFixtures.BIRTH_DATE.toString())
                .containsEntry("published_at", null)
                .containsKey("occurred_at");
        assertThat(rows.get(0).get("payload_event_id")).isEqualTo(rows.get(0).get("event_id"));
        assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM outbox_events", Integer.class))
                .isZero();
    }

    @Test
    void rejectedCreationRecordsNoRow() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO customers (id, name, security_number, birth_date, registered_at, status)"
                        + " VALUES (gen_random_uuid(), 'Already Here', ?, DATE '1980-01-01', now(), 'ACTIVE')",
                CustomerFixtures.SECURITY_NUMBER);

        mockMvc.perform(post(CustomerController.BASE_PATH)
                        .header("Idempotency-Key", "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b91")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CustomerFixtures.requestJson()))
                .andExpect(status().isConflict());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class))
                .isZero();
    }

    @Test
    void idempotentReplayRecordsNoSecondRow() throws Exception {
        String key = "0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b92";
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post(CustomerController.BASE_PATH)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CustomerFixtures.requestJson()))
                    .andExpect(status().isCreated());
        }

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class))
                .isEqualTo(1);
    }
}
