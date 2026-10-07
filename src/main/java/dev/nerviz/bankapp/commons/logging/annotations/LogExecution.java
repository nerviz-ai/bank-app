package dev.nerviz.bankapp.commons.logging.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method-level logging annotation for application/adapter code.
 * {@code .claude/rules/logging.md} § Domain forbids this in the domain layer — apply only in
 * application services and adapters.
 *
 * <p>Lives in the {@code annotations} sub-package of {@code commons.logging}, same reasoning as
 * {@code HttpMethodLogExecution}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogExecution {

    boolean logReturn() default true;

    boolean logParameters() default true;
}
