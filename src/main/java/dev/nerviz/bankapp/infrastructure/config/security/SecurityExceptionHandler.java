package dev.nerviz.bankapp.infrastructure.config.security;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A denial raised inside the MVC layer (the actor resolver refusing a customer token) is answered
 * 403 here, with the body the filter chain writes. Ordered ahead of {@code ApiExceptionHandler}:
 * its catch-all would otherwise answer 500. The body names no reason; the reason goes to the log.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityExceptionHandler {

    SecurityProblemWriter writer;

    public SecurityExceptionHandler(Tracer tracer) {
        this.writer = new SecurityProblemWriter(tracer);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException e, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        log.warn(
                "Access denied at {} {}: {}",
                request.getMethod(),
                request.getRequestURI(),
                e.getClass().getSimpleName());
        writer.write(request, response, HttpStatus.FORBIDDEN);
    }
}
