/**
 * Application layer. Use cases — one per business operation, orchestrating the domain
 * through its ports. Grouped one subpackage per aggregate, e.g.
 * {@code application.usecase.<aggregate>}.
 *
 * <p>Depends on ports (interfaces), never on an {@code infrastructure} implementation.
 * See {@code .claude/rules/architecture-ddd.md}.
 */
package dev.nerviz.bankapp.application.usecase;
