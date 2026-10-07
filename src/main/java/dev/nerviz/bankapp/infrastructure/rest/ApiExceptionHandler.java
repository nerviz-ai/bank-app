package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.domain.exception.BusinessRuleViolationException;
import dev.nerviz.bankapp.domain.exception.ConflictException;
import dev.nerviz.bankapp.domain.exception.DomainException;
import dev.nerviz.bankapp.domain.exception.NotFoundException;
import dev.nerviz.bankapp.domain.exception.ValidationException;
import dev.nerviz.bankapp.infrastructure.rest.idempotent.IdempotencyKeyInterceptor;
import dev.nerviz.bankapp.infrastructure.rest.idempotent.MissingIdempotencyKeyException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates the typed family from {@.claude/rules/error-handling.md} into
 * {@code ProblemDetail} (RFC 7807). {@code traceId} comes from Micrometer Tracing — the
 * tracing bridge and the Boot OpenTelemetry starter are on the classpath, which is what
 * registers the {@code Tracer} bean on Spring Boot 4.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Tracer tracer;

    public ApiExceptionHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException e, HttpServletRequest request) {
        return problemDetail(HttpStatus.NOT_FOUND, e, request);
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidation(ValidationException e, HttpServletRequest request) {
        return problemDetail(HttpStatus.BAD_REQUEST, e, request);
    }

    /** UNPROCESSABLE_CONTENT (RFC 9110); the number is still 422. */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRuleViolation(BusinessRuleViolationException e, HttpServletRequest request) {
        return problemDetail(HttpStatus.UNPROCESSABLE_CONTENT, e, request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException e, HttpServletRequest request) {
        return problemDetail(HttpStatus.CONFLICT, e, request);
    }

    /**
     * Structural, same family as bean validation below: the request never reached the use
     * case, so there's no domain exception and no {@code errorCode}.
     */
    @ExceptionHandler(MissingIdempotencyKeyException.class)
    public ProblemDetail handleMissingIdempotencyKey(MissingIdempotencyKeyException e, HttpServletRequest request) {
        log.debug("Missing/malformed Idempotency-Key at {}", request.getRequestURI());
        return invalidRequest(request, List.of(new Violation(IdempotencyKeyInterceptor.HEADER, e.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<Violation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toViolation)
                .toList();
        log.debug("Bean validation failed at {}: {}", request.getRequestURI(), violations);
        return invalidRequest(request, violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.debug("Type mismatch for {} at {}", e.getName(), request.getRequestURI());
        return invalidRequest(request, List.of(new Violation(e.getName(), "has an invalid format")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        log.debug("Unreadable body at {}", request.getRequestURI(), e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request body");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /** The only untyped exception allowed here: the handler that catches what escaped. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e, HttpServletRequest request) {
        String traceId = currentTraceId();
        log.error("Unmapped error at {} [traceId={}]", request.getRequestURI(), traceId, e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId);
        return problem;
    }

    private static ProblemDetail invalidRequest(HttpServletRequest request, List<Violation> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("violations", violations);
        return problem;
    }

    private ProblemDetail problemDetail(HttpStatus status, DomainException e, HttpServletRequest request) {
        log.warn("{} at {}: {}", e.errorCode(), request.getRequestURI(), e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", e.errorCode());
        return problem;
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        return span == null ? "unavailable" : span.context().traceId();
    }

    private static Violation toViolation(FieldError error) {
        return new Violation(error.getField(), error.getDefaultMessage());
    }

    public record Violation(String field, String message) {}
}
