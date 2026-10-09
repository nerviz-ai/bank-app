package dev.nerviz.bankapp.application.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.exception.ValidationException;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OutboxRetryPolicyTest {

    @Test
    void acceptsCeilingAndBackoffBounds() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(10, Duration.ofSeconds(1), Duration.ofMinutes(5));

        assertThat(policy.maxAttempts()).isEqualTo(10);
    }

    @ParameterizedTest
    @MethodSource("invalidPolicies")
    void rejectsInvalidValues(int maxAttempts, Duration base, Duration max, String errorCode) {
        assertThatThrownBy(() -> new OutboxRetryPolicy(maxAttempts, base, max))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }

    static Stream<Arguments> invalidPolicies() {
        Duration second = Duration.ofSeconds(1);
        Duration minute = Duration.ofMinutes(1);
        return Stream.of(
                Arguments.of(0, second, minute, "OUTBOX_MAX_ATTEMPTS_INVALID"),
                Arguments.of(10, Duration.ZERO, minute, "OUTBOX_BACKOFF_BASE_INVALID"),
                Arguments.of(10, null, minute, "OUTBOX_BACKOFF_BASE_INVALID"),
                Arguments.of(10, second, Duration.ZERO, "OUTBOX_BACKOFF_MAX_INVALID"),
                Arguments.of(10, minute, second, "OUTBOX_BACKOFF_MAX_INVALID"));
    }
}
