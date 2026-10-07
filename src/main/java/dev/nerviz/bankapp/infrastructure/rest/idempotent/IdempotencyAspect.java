package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import dev.nerviz.bankapp.application.port.IdempotencyRequest;
import dev.nerviz.bankapp.application.port.StoredResponse;
import dev.nerviz.bankapp.application.shared.IdempotentExecution;
import dev.nerviz.bankapp.application.shared.IdempotentOutcome;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Clock;
import java.util.UUID;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Delegates the whole transaction shape to {@link IdempotentExecution} — this class only
 * adapts a join point to the shape that component takes. It never opens a transaction
 * itself ({@.claude/rules/architecture-ddd.md} § Application).
 */
@Aspect
@Component
class IdempotencyAspect {

    private static final String CALLER_PLACEHOLDER = "anonymous";

    private final IdempotentExecution idempotentExecution;
    private final IdempotencyRequestHasher requestHasher;
    private final IdempotencyResponseCodec responseCodec;
    private final Clock clock;

    IdempotencyAspect(
            IdempotentExecution idempotentExecution,
            IdempotencyRequestHasher requestHasher,
            IdempotencyResponseCodec responseCodec,
            Clock clock) {
        this.idempotentExecution = idempotentExecution;
        this.requestHasher = requestHasher;
        this.responseCodec = responseCodec;
        this.clock = clock;
    }

    /**
     * No {@code throws Throwable} here: {@code proceedUnchecked} below crosses that
     * boundary with Lombok's {@code @SneakyThrows} instead of an explicit catch, so
     * whatever the endpoint threw — including a checked type — reaches the caller
     * exactly as thrown, with no wrapper to unwrap.
     */
    @Around("@annotation(idempotent)")
    Object aroundIdempotent(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        HttpServletRequest httpRequest = currentHttpRequest();
        UUID key = UUID.fromString(httpRequest.getHeader(IdempotencyKeyInterceptor.HEADER));
        Object requestBody = joinPoint.getArgs()[idempotent.bodyArgIndex()];
        String route = httpRequest.getMethod() + " " + httpRequest.getRequestURI();
        IdempotencyRequest request = new IdempotencyRequest(
                key, route, CALLER_PLACEHOLDER, requestHasher.hash(requestBody), clock.instant());

        IdempotentOutcome<ResponseEntity<Object>> outcome =
                idempotentExecution.execute(request, () -> proceedUnchecked(joinPoint), responseCodec::encode);
        return switch (outcome) {
            case IdempotentOutcome.Executed<ResponseEntity<Object>>(ResponseEntity<Object> response) -> response;
            case IdempotentOutcome.Replayed<ResponseEntity<Object>>(StoredResponse stored) ->
                responseCodec.decode(stored, responseBodyType(joinPoint));
        };
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    private ResponseEntity<Object> proceedUnchecked(ProceedingJoinPoint joinPoint) {
        return (ResponseEntity<Object>) joinPoint.proceed();
    }

    private HttpServletRequest currentHttpRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    private static Type responseBodyType(ProceedingJoinPoint joinPoint) {
        Type genericReturnType =
                ((MethodSignature) joinPoint.getSignature()).getMethod().getGenericReturnType();
        if (genericReturnType instanceof ParameterizedType parameterized) {
            return parameterized.getActualTypeArguments()[0];
        }
        return Object.class;
    }
}
