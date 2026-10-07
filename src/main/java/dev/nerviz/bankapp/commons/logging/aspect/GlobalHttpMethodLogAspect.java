package dev.nerviz.bankapp.commons.logging.aspect;

import dev.nerviz.bankapp.commons.logging.LoggingCommonsMethods;
import dev.nerviz.bankapp.commons.logging.properties.GlobalProperties;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.FieldDefaults;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Default-on: every {@code @GetMapping}/{@code @PostMapping}/{@code @PutMapping}/
 * {@code @PatchMapping}/{@code @DeleteMapping} method logs params + response unless
 * {@code app.logging.http-method.enabled=false}. A response DTO with a PII field needs
 * {@code LogMask} + {@code @MaskSensitiveData} (see {@code LogMask.java}) before this aspect
 * ever sees it — {@code .claude/rules/logging.md}'s "zero raw sensitive data" line applies here
 * first.
 *
 * <p>Lives in the {@code aspect} sub-package of {@code commons.logging}, with
 * {@code HttpMethodLogExecutionAspect} and {@code LogExecutionAspect} — every {@code @Around}
 * advice grouped apart from the annotations it advises and the property/utility types it
 * depends on.
 */
@Aspect
@Component
@EnableConfigurationProperties(GlobalProperties.class)
@ConditionalOnProperty(
        prefix = "app.logging.http-method",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GlobalHttpMethodLogAspect {

    GlobalProperties globalProperties;

    @Around("@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PatchMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.DeleteMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.GetMapping)")
    @SneakyThrows
    public Object logExecutionHttpMethod(ProceedingJoinPoint joinPoint) {
        if (!isLoggingEnabled()) {
            return joinPoint.proceed();
        }
        return LoggingCommonsMethods.logInterceptJoinPoint(joinPoint);
    }

    private boolean isLoggingEnabled() {
        var httpMethod = globalProperties.httpMethod();
        return httpMethod == null || Boolean.TRUE.equals(httpMethod.enabled());
    }
}
