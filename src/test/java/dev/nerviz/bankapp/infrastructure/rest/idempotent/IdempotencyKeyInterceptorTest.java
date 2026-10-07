package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** Structural half of idempotency: header presence and format. No Spring context. */
class IdempotencyKeyInterceptorTest {

    private final IdempotencyKeyInterceptor interceptor = new IdempotencyKeyInterceptor();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final HandlerMethod idempotentHandler = handlerMethod("annotated");
    private final HandlerMethod plainHandler = handlerMethod("plain");

    @Test
    void rejectsMissingKey() {
        MockHttpServletRequest request = request(null);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, idempotentHandler))
                .isInstanceOf(MissingIdempotencyKeyException.class)
                .hasMessageContaining(IdempotencyKeyInterceptor.HEADER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "123"})
    void rejectsMalformedKey(String malformed) {
        MockHttpServletRequest request = request(malformed);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, idempotentHandler))
                .isInstanceOf(MissingIdempotencyKeyException.class)
                .hasMessageContaining(IdempotencyKeyInterceptor.HEADER);
    }

    @Test
    void acceptsUuidKey() {
        MockHttpServletRequest request = request("0f2b6c1e-4d3a-4f5b-9c8d-7e6f5a4b3c2d");

        assertThat(interceptor.preHandle(request, response, idempotentHandler)).isTrue();
    }

    @Test
    void ignoresHandlerWithoutIdempotent() {
        MockHttpServletRequest request = request(null);

        assertThat(interceptor.preHandle(request, response, plainHandler)).isTrue();
    }

    private static MockHttpServletRequest request(String key) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/customers");
        if (key != null) {
            request.addHeader(IdempotencyKeyInterceptor.HEADER, key);
        }
        return request;
    }

    private static HandlerMethod handlerMethod(String methodName) {
        Method method = org.springframework.util.ReflectionUtils.findMethod(ProbeController.class, methodName);
        return new HandlerMethod(new ProbeController(), method);
    }

    /** Local probe exposing one annotated and one plain method to drive the interceptor. */
    static class ProbeController {

        @Idempotent
        public void annotated() {
            // probe method, body intentionally empty
        }

        public void plain() {
            // probe method, body intentionally empty
        }
    }
}
