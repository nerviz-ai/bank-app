package dev.nerviz.bankapp.commons.logging.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.logging.*}, not {@code spring.*}: Spring reserves that prefix for its own
 * properties.
 *
 * <p>Lives in the {@code properties} sub-package of {@code commons.logging}, with
 * {@code HttpMethodProperties}.
 */
@ConfigurationProperties(prefix = "app.logging")
public record GlobalProperties(HttpMethodProperties httpMethod) {}
