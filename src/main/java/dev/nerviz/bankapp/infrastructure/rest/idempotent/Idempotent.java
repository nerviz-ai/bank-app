package dev.nerviz.bankapp.infrastructure.rest.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Single source of truth both {@link IdempotencyKeyInterceptor} (structural check) and
 * {@link IdempotencyAspect} (transactional check) read.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Idempotent {

    /** Index, among the annotated method's parameters, of the request body to hash. */
    int bodyArgIndex() default 0;
}
