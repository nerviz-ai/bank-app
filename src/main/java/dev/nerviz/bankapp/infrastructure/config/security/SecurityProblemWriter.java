package dev.nerviz.bankapp.infrastructure.config.security;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;

/**
 * Writes the 401 and 403 Problem Details body from the filter chain. The chain runs before MVC,
 * and handing the exception to the MVC resolvers would reach the catch-all of
 * {@code ApiExceptionHandler} (500): the body is written here, with the same converter family
 * and the same shape. The body names no reason — no missing role, no token error.
 */
final class SecurityProblemWriter {

    private final JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();
    private final Tracer tracer;

    SecurityProblemWriter(Tracer tracer) {
        this.tracer = tracer;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", currentTraceId());
        response.setStatus(status.value());
        converter.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        return span == null ? "unavailable" : span.context().traceId();
    }
}
