/**
 * Cross-cutting application-layer infrastructure that belongs to no single
 * aggregate or use case — e.g. idempotent execution support.
 *
 * <p>Depends on ports (interfaces), never on an {@code infrastructure} implementation.
 * See {@code .claude/rules/architecture-ddd.md}.
 */
package dev.nerviz.bankapp.application.shared;
