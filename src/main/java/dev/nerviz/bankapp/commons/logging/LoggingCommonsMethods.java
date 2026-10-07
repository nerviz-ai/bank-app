package dev.nerviz.bankapp.commons.logging;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Single writer of the init/finish log lines every aspect in this package
 * funnels through, so {@code logback-spring.xml}'s pattern only has one call site to match.
 */
@Slf4j
@UtilityClass
public class LoggingCommonsMethods {

    @SneakyThrows
    public Object logInterceptJoinPoint(ProceedingJoinPoint joinPoint, LoggingOptions options) {
        long start = System.nanoTime();
        var call = Call.of(joinPoint);
        logInit(call, options);

        Object result = joinPoint.proceed();

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        logFinish(call, new Outcome(result, elapsedMs), options);
        return result;
    }

    @SneakyThrows
    public Object logInterceptJoinPoint(ProceedingJoinPoint joinPoint) {
        return logInterceptJoinPoint(joinPoint, LoggingOptions.ALL);
    }

    public String mask(String value, String regex) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll(regex, "*");
    }

    private void logInit(Call call, LoggingOptions options) {
        if (options.logParameters()) {
            log.info("stage=init, method={}, class={}, parameters={}", call.method(), call.type(), call.args());
        } else {
            log.info("stage=init, method={}, class={}", call.method(), call.type());
        }
    }

    private void logFinish(Call call, Outcome outcome, LoggingOptions options) {
        if (options.logParameters() && options.logReturn()) {
            logFinishWithParametersAndResult(call, outcome);
            return;
        }
        if (options.logParameters()) {
            logFinishWithParameters(call, outcome);
            return;
        }
        if (options.logReturn()) {
            logFinishWithResult(call, outcome);
            return;
        }
        log.info(
                "stage=finish, method={}, class={}, time-execution={}ms",
                call.method(),
                call.type(),
                outcome.elapsedMs());
    }

    private void logFinishWithParametersAndResult(Call call, Outcome outcome) {
        log.info(
                "stage=finish, method={}, class={}, parameters={}, result={}, time-execution={}ms",
                call.method(),
                call.type(),
                call.args(),
                outcome.result(),
                outcome.elapsedMs());
    }

    private void logFinishWithParameters(Call call, Outcome outcome) {
        log.info(
                "stage=finish, method={}, class={}, parameters={}, time-execution={}ms",
                call.method(),
                call.type(),
                call.args(),
                outcome.elapsedMs());
    }

    private void logFinishWithResult(Call call, Outcome outcome) {
        log.info(
                "stage=finish, method={}, class={}, result={}, time-execution={}ms",
                call.method(),
                call.type(),
                outcome.result(),
                outcome.elapsedMs());
    }

    /**
     * `List`, not `Object[]`: a record with an array component gets identity-based
     * `equals`/`hashCode` and a `toString` of `[Ljava.lang.Object;@…` — a Sonar bug
     * (java:S6218). `Arrays.asList`, not `List.of`: an argument may be null. The log line
     * prints the same `[a, b]` either way.
     */
    private record Call(String method, String type, List<Object> args) {
        static Call of(ProceedingJoinPoint joinPoint) {
            return new Call(
                    joinPoint.getSignature().getName(),
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    Arrays.asList(joinPoint.getArgs()));
        }
    }

    private record Outcome(Object result, long elapsedMs) {}
}
