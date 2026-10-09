package dev.nerviz.bankapp.infrastructure.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.KafkaContainerConfiguration;
import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.application.port.OutboxRetryPolicy;
import dev.nerviz.bankapp.application.port.RelayOutcome;
import dev.nerviz.bankapp.application.port.RequestKycVerification;
import dev.nerviz.bankapp.application.usecase.outbox.RelayOutboxEventsCommand;
import dev.nerviz.bankapp.application.usecase.outbox.RelayOutboxEventsUseCase;
import dev.nerviz.bankapp.domain.event.KycVerificationRequested;
import dev.nerviz.bankapp.domain.model.Customer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serialization and broker acknowledgement only fail against a real broker. The row is appended
 * through the real port inside a transaction, the relay pass is called directly (the trigger is
 * off under the test profile), and the record is read back from the topic with a plain consumer.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaContainerConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.kafka.admin.auto-create=true")
@EnabledIf("dockerAvailable")
@Sql(statements = "DELETE FROM outbox_events", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OutboxRelayKafkaIT {

    private static final RelayOutboxEventsCommand COMMAND = new RelayOutboxEventsCommand(
            10, Duration.ofMinutes(8), new OutboxRetryPolicy(10, Duration.ofSeconds(1), Duration.ofMinutes(5)));

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private RequestKycVerification requestKycVerification;

    @Autowired
    private RelayOutboxEventsUseCase relayOutboxEvents;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void relayPublishesRowToTopicAndMarksItPublished() {
        Customer customer = CustomerFixtures.customer();
        String customerId = customer.id().value().toString();
        transactionTemplate.executeWithoutResult(
                status -> requestKycVerification.request(KycVerificationRequested.of(customer)));

        RelayOutcome outcome = relayOutboxEvents.relayPending(COMMAND);

        assertThat(outcome).isEqualTo(new RelayOutcome(1, 0, 0));
        ConsumerRecord<String, String> delivered = readFromTopic(customerId);
        assertThat(delivered.key()).isEqualTo(customerId);
        assertThat(delivered.headers().lastHeader("__TypeId__")).isNull();
        JsonNode value = jsonMapper.readTree(delivered.value());
        assertThat(value.isObject())
                .as("value is a JSON object, not a quoted string")
                .isTrue();
        assertThat(value.get("customerId").asString()).isEqualTo(customerId);
        assertThat(value.get("name").asString()).isEqualTo(CustomerFixtures.NAME);
        assertThat(value.get("securityNumber").asString()).isEqualTo(CustomerFixtures.SECURITY_NUMBER);
        assertThat(value.get("birthDate").asString()).isEqualTo(CustomerFixtures.BIRTH_DATE.toString());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT published_at IS NOT NULL FROM outbox_events WHERE event_id = ?::uuid",
                        Boolean.class,
                        value.get("eventId").asString()))
                .isTrue();
    }

    private ConsumerRecord<String, String> readFromTopic(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-relay-it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(TopicResolver.KYC_VERIFICATION_REQUESTED_TOPIC));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                assertThat(received).anyMatch(candidate -> key.equals(candidate.key()));
            });
            return received.stream()
                    .filter(candidate -> key.equals(candidate.key()))
                    .findFirst()
                    .orElseThrow();
        }
    }
}
