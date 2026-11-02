package dev.nerviz.bankapp.infrastructure.rest;

import dev.nerviz.bankapp.domain.model.Actor;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.infrastructure.config.security.Roles;
import dev.nerviz.bankapp.infrastructure.config.security.TokenAuthoritiesProperties;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The only class that reads an {@code Authentication} to build an {@link Actor}. {@code ROLE_OPERATOR}
 * acts for anyone; {@code ROLE_CUSTOMER} acts for the customer its token names in the configured
 * claim. A customer token without a parseable id never becomes an actor with no id: it is a denial,
 * answered 403 by {@code SecurityExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CurrentActorResolver {

    private static final String OPERATOR_AUTHORITY = "ROLE_" + Roles.OPERATOR;
    private static final String CUSTOMER_AUTHORITY = "ROLE_" + Roles.CUSTOMER;

    TokenAuthoritiesProperties properties;

    /**
     * @throws AccessDeniedException a customer token without a valid customer id claim, or no usable role
     */
    public Actor resolve(Authentication authentication) {
        if (hasAuthority(authentication, OPERATOR_AUTHORITY)) {
            return new Actor.Operator();
        }
        if (hasAuthority(authentication, CUSTOMER_AUTHORITY) && authentication.getPrincipal() instanceof Jwt jwt) {
            return customerId(jwt)
                    .<Actor>map(Actor.Customer::new)
                    .orElseThrow(() -> new AccessDeniedException("Customer token without a valid customer id claim"));
        }
        throw new AccessDeniedException("No role that opens an account");
    }

    private Optional<CustomerId> customerId(Jwt jwt) {
        if (!(jwt.getClaims().get(properties.customerIdClaim()) instanceof String text)) {
            return Optional.empty();
        }
        try {
            return Optional.of(CustomerId.of(UUID.fromString(text)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
