package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Structural half of idempotency: header presence and format. {@code @Idempotent} is the
 * single source of truth this interceptor and {@link IdempotencyAspect} both read.
 */
@Component
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Idempotency-Key";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (requiresKey(handler)) {
            requireValidKey(request);
        }
        return true;
    }

    private static void requireValidKey(HttpServletRequest request) {
        String key = request.getHeader(HEADER);
        if (key == null || !isUuid(key)) {
            throw new MissingIdempotencyKeyException("Header " + HEADER + " missing or malformed");
        }
    }

    private boolean requiresKey(Object handler) {
        return handler instanceof HandlerMethod handlerMethod && handlerMethod.hasMethodAnnotation(Idempotent.class);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
