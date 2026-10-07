package dev.nerviz.bankapp.commons.logging.interfaces;

import dev.nerviz.bankapp.commons.logging.LoggingCommonsMethods;
import dev.nerviz.bankapp.commons.logging.annotations.MaskSensitiveData;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import lombok.SneakyThrows;
import org.springframework.util.ReflectionUtils;

/**
 * A DTO or adapter-layer record that overrides {@code toString()} to call
 * {@code mask(this)} gets every {@code @MaskSensitiveData} field masked automatically wherever
 * it's interpolated into a log line (e.g. by {@code GlobalHttpMethodLogAspect}'s default-on
 * response logging). Never implemented by a domain type — {@code .claude/rules/logging.md}:
 * the domain logs nothing, so it has nothing to mask.
 *
 * <p>Lives in the {@code interfaces} sub-package of {@code commons.logging} — the one type here
 * a DTO elsewhere in the project implements, kept apart from the annotation/aspect/enum
 * machinery it reaches into.
 */
public interface LogMask extends Serializable {

    Set<Class<?>> MASKABLE_TYPES = Set.of(
            String.class,
            BigDecimal.class,
            LocalDate.class,
            LocalDateTime.class,
            Boolean.class,
            Integer.class,
            Float.class,
            Short.class,
            Double.class);

    @SneakyThrows
    default String mask(Object object) {
        Class<?> clazz = object.getClass();
        StringJoiner joiner = new StringJoiner(", ", clazz.getSimpleName() + "{", "}");
        for (Field field : clazz.getDeclaredFields()) {
            ReflectionUtils.makeAccessible(field);
            Object value = field.get(object);
            if (shouldMask(field, value)) {
                value = LoggingCommonsMethods.mask(value.toString(), regexFor(field));
            }
            joiner.add(field.getName() + "=" + value);
        }
        return joiner.toString();
    }

    private static String regexFor(Field field) {
        MaskSensitiveData annotation = Objects.requireNonNull(field.getAnnotation(MaskSensitiveData.class));
        return annotation.customMaskRegex().isBlank()
                ? annotation.maskedType().getRegex()
                : annotation.customMaskRegex();
    }

    private static boolean shouldMask(Field field, Object value) {
        return field.isAnnotationPresent(MaskSensitiveData.class)
                && (value.getClass().isPrimitive() || MASKABLE_TYPES.contains(value.getClass()));
    }
}
