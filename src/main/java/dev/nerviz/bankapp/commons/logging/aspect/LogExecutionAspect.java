package dev.nerviz.bankapp.commons.logging.aspect;

import dev.nerviz.bankapp.commons.logging.LoggingCommonsMethods;
import dev.nerviz.bankapp.commons.logging.LoggingOptions;
import dev.nerviz.bankapp.commons.logging.annotations.LogExecution;
import java.util.Objects;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Advice for {@code @LogExecution}.
 *
 * <p>Lives in the {@code aspect} sub-package of {@code commons.logging}, same reasoning as
 * {@code GlobalHttpMethodLogAspect}.
 */
@Aspect
@Component
public class LogExecutionAspect {

    @Around("@annotation(dev.nerviz.bankapp.commons.logging.annotations.LogExecution)")
    @SneakyThrows
    public Object logExecution(ProceedingJoinPoint joinPoint) {
        var signature = (MethodSignature) joinPoint.getSignature();
        var annotation = Objects.requireNonNull(signature.getMethod().getAnnotation(LogExecution.class));
        var options = new LoggingOptions(annotation.logReturn(), annotation.logParameters());
        return LoggingCommonsMethods.logInterceptJoinPoint(joinPoint, options);
    }
}
