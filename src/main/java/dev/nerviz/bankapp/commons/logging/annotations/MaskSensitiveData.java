package dev.nerviz.bankapp.commons.logging.annotations;

import dev.nerviz.bankapp.commons.logging.enums.MaskedType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Field-level masking for a class that implements {@code LogMask}. This is the
 * mechanism {@code .claude/rules/logging.md} cites for putting PII into a log line without it
 * being raw: mask it here, never log the unmasked value directly.
 *
 * <p>Lives in the {@code annotations} sub-package of {@code commons.logging}; {@code MaskedType}
 * sits in the sibling {@code enums} sub-package, one hop away since it's this annotation's only
 * default.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaskSensitiveData {

    MaskedType maskedType() default MaskedType.ALL;

    /**
     * Takes precedence over {@link #maskedType()} when non-blank.
     */
    String customMaskRegex() default "";
}
