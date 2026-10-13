package dev.nerviz.bankapp.infrastructure.config.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * The bearer scheme declared in the OpenAPI document, required globally; a public operation
 * opts out with an empty {@code @SecurityRequirements} on its own docs annotation.
 * Documentation only: the filter chain is the enforcement.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiSecurityConfig.SCHEME))
@SecurityScheme(
        name = OpenApiSecurityConfig.SCHEME,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiSecurityConfig {

    public static final String SCHEME = "bearer-token";
}
