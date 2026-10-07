package dev.nerviz.bankapp.commons.logging.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-endpoint override of {@code GlobalHttpMethodLogAspect}'s default. Apply on a
 * {@code @RestController} method that needs a different {@code logReturn}/{@code logParameters}
 * than the global default.
 *
 * <p>Lives in the {@code annotations} sub-package of {@code commons.logging}, next to
 * {@code LogExecution} and {@code MaskSensitiveData} — the annotation surface split from the
 * aspects that advise them ({@code aspect} sub-package) and from the enums/interfaces/properties
 * each one needs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpMethodLogExecution {

    boolean logReturn() default true;

    boolean logParameters() default true;
}
