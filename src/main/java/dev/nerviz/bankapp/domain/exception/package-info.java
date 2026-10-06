/**
 * Domain exception family — typed exceptions for each invariant violation, never a
 * generic {@code RuntimeException} or {@code Exception}.
 *
 * <p>No framework: no {@code org.springframework}, {@code jakarta.*},
 * {@code com.fasterxml.jackson}, or {@code tools.jackson}; no import of
 * {@code application} or {@code infrastructure}. See {@code .claude/rules/architecture-ddd.md}
 * and {@code .claude/rules/error-handling.md}.
 */
package dev.nerviz.bankapp.domain.exception;
