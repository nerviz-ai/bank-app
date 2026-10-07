package dev.nerviz.bankapp.commons.logging;

/**
 * One record instead of the two-boolean parameter list the ported library
 * used ({@code logInterceptJoinPoint(joinPoint, boolean, boolean)}), which {@code code-quality.md}
 * forbids: "A boolean parameter is forbidden — that's two functions with different
 * names." Both flags together are one decision (what to log), so one immutable
 * parameter object, not two positional booleans a caller can transpose by mistake.
 */
public record LoggingOptions(boolean logReturn, boolean logParameters) {

    public static final LoggingOptions ALL = new LoggingOptions(true, true);
}
