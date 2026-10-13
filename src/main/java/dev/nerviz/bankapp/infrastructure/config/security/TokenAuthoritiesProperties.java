package dev.nerviz.bankapp.infrastructure.config.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.security.token.*} — where the identity provider puts each kind of authority.
 * Dotted paths reach nested claims. Defaults fit a provider that issues flat {@code roles}
 * and {@code scope} claims.
 */
@ConfigurationProperties("app.security.token")
public record TokenAuthoritiesProperties(
        @DefaultValue("sub") String principalClaim,
        @DefaultValue("roles") List<String> rolesClaims,
        @DefaultValue("scope") List<String> scopesClaims) {}
