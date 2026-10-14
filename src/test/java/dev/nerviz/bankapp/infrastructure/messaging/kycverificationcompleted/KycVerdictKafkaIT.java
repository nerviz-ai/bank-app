package dev.nerviz.bankapp.infrastructure.messaging.kycverificationcompleted;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.nerviz.bankapp.CustomerFixtures;
import dev.nerviz.bankapp.KafkaContainerConfiguration;
import dev.nerviz.bankapp.TestcontainersConfiguration;
import dev.nerviz.bankapp.application.port.CustomerRepository;
import dev.nerviz.bankapp.domain.model.Customer;
import dev.nerviz.bankapp.domain.model.CustomerStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

/**
 * End to end through a real broker: conversion, the error handler's classification, the
 * dead-letter recoverer and offset commits are container behaviour, not unit-testable
 * (40-tests.md § 2). The listener container is turned back on against a Testcontainers broker
 * — {@code application-test.yml} otherwise leaves it off.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, KafkaContainerConfiguration.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.kafka.admin.auto-create=true", "spring.kafka.listener.auto-startup=true"})
@EnabledIf("dockerAvailable")
@Sql(statements = "DELETE FROM customers", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KycVerdictKafkaIT {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(15);

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaContainer kafka;

    @Test
    void approvedVerdictActivatesCustomer() {
        Customer customer = CustomerFixtures.customer();
        customerRepository.save(customer);

        publish(KycVerificationCompletedTopicConfig.TOPIC, customer.id().value(), verdictJson(customer, "APPROVED"));

        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(() -> assertThat(statusOf(customer)).contains(CustomerStatus.ACTIVE));
    }

    @Test
    void rejectVerdictRejectsCustomer() {
        Customer customer = CustomerFixtures.customer();
        customerRepository.save(customer);

        publish(KycVerificationCompletedTopicConfig.TOPIC, customer.id().value(), verdictJson(customer, "REJECT"));

        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(() -> assertThat(statusOf(customer)).contains(CustomerStatus.REJECTED_BY_KYC));
    }

    @Test
    void duplicateVerdictKeepsStatusAndSkipsDlq() {
        Customer customer = CustomerFixtures.customer();
        customerRepository.save(customer);

        publish(KycVerificationCompletedTopicConfig.TOPIC, customer.id().value(), verdictJson(customer, "APPROVED"));
        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(() -> assertThat(statusOf(customer)).contains(CustomerStatus.ACTIVE));
        publish(KycVerificationCompletedTopicConfig.TOPIC, customer.id().value(), verdictJson(customer, "APPROVED"));

        await().atMost(AWAIT_TIMEOUT)
                .untilAsserted(() -> assertThat(versionOf(customer)).isEqualTo(1L));
        assertThat(statusOf(customer)).contains(CustomerStatus.ACTIVE);
        assertThat(pollDlq(customer.id().value().toString())).isEmpty();
    }

    @Test
    void conflictingVerdictGoesToDlq() {
        Customer activeCustomer = CustomerFixtures.withStatus(CustomerStatus.ACTIVE);
        customerRepository.save(activeCustomer);

        publish(
                KycVerificationCompletedTopicConfig.TOPIC,
                activeCustomer.id().value(),
                verdictJson(activeCustomer, "REJECT"));

        Optional<ConsumerRecord<String, String>> dlqRecord =
                pollDlq(activeCustomer.id().value().toString());
        assertThat(dlqRecord).isPresent();
        assertThat(statusOf(activeCustomer)).contains(CustomerStatus.ACTIVE);
    }

    @Test
    void unknownCustomerGoesToDlq() {
        UUID unknownId = UUID.randomUUID();

        publish(
                KycVerificationCompletedTopicConfig.TOPIC,
                unknownId,
                "{\"eventId\":\"%s\",\"customerId\":\"%s\",\"verdict\":\"APPROVED\"}"
                        .formatted(UUID.randomUUID(), unknownId));

        Optional<ConsumerRecord<String, String>> dlqRecord = pollDlq(unknownId.toString());
        assertThat(dlqRecord).isPresent();
        assertThat(jdbcTemplate.queryForList("SELECT id FROM customers WHERE id = ?::uuid", unknownId))
                .isEmpty();
    }

    @ParameterizedTest
    @MethodSource("malformedMessages")
    void malformedMessageGoesToDlq(String malformedValue) {
        Customer customer = CustomerFixtures.customer();
        customerRepository.save(customer);

        publish(KycVerificationCompletedTopicConfig.TOPIC, customer.id().value(), malformedValue);

        Optional<ConsumerRecord<String, String>> dlqRecord =
                pollDlq(customer.id().value().toString());
        assertThat(dlqRecord).isPresent();
        assertThat(statusOf(customer)).contains(CustomerStatus.KYC_IN_PROGRESS);
    }

    private static Stream<Arguments> malformedMessages() {
        return Stream.of(Arguments.of("not-json-at-all"), Arguments.of("{\"customerId\":\"x\",\"verdict\":\"MAYBE\"}"));
    }

    private void publish(String topic, UUID key, String value) {
        Map<String, Object> config = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
            producer.send(new ProducerRecord<>(topic, key.toString(), value)).get();
        } catch (Exception ex) {
            throw new IllegalStateException("could not publish test message", ex);
        }
    }

    private Optional<ConsumerRecord<String, String>> pollDlq(String key) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG,
                "kyc-verdict-it-dlq-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(KycVerificationCompletedTopicConfig.DLQ_TOPIC));
            List<ConsumerRecord<String, String>> received = new ArrayList<>();
            try {
                await().atMost(AWAIT_TIMEOUT).untilAsserted(() -> {
                    consumer.poll(Duration.ofMillis(500)).forEach(received::add);
                    assertThat(received).anyMatch(candidate -> key.equals(candidate.key()));
                });
            } catch (ConditionTimeoutException timeout) {
                return Optional.empty();
            }
            return received.stream()
                    .filter(candidate -> key.equals(candidate.key()))
                    .findFirst();
        }
    }

    private Optional<CustomerStatus> statusOf(Customer customer) {
        return customerRepository.findById(customer.id()).map(Customer::status);
    }

    private long versionOf(Customer customer) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM customers WHERE id = ?::uuid",
                Long.class,
                customer.id().value().toString());
    }

    private static String verdictJson(Customer customer, String verdict) {
        return "{\"eventId\":\"%s\",\"customerId\":\"%s\",\"verdict\":\"%s\"}"
                .formatted(UUID.randomUUID(), customer.id().value(), verdict);
    }
}
