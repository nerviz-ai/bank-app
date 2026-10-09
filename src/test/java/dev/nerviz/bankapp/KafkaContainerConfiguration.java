package dev.nerviz.bankapp;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real single-node broker, imported only by the test that needs one — every other context
 * runs with {@code spring.kafka.admin.auto-create: false} and starts none. The tag is the one
 * the pending compose service uses ({@code java .claude/hooks/ArchHook.java compose} checks
 * the pair).
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaContainerConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.0"));
    }
}
