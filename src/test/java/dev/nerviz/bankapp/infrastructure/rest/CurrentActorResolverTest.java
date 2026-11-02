package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.nerviz.bankapp.domain.model.Actor;
import dev.nerviz.bankapp.domain.model.CustomerId;
import dev.nerviz.bankapp.infrastructure.config.security.Roles;
import dev.nerviz.bankapp.infrastructure.config.security.TokenAuthoritiesProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentActorResolverTest {

    private static final String CLAIM = "customer_id";

    private final CurrentActorResolver resolver =
            new CurrentActorResolver(new TokenAuthoritiesProperties("sub", List.of("roles"), List.of("scope"), CLAIM));

    private static Authentication jwtAuthentication(String role, Map<String, Object> extraClaims) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("someone")
                .issuedAt(Instant.parse("2026-01-15T10:00:00Z"))
                .expiresAt(Instant.parse("2026-01-15T11:00:00Z"))
                .claims(claims -> claims.putAll(extraClaims))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void resolvesOperator() {
        Authentication authentication = jwtAuthentication(Roles.OPERATOR, Map.of());

        Actor actor = resolver.resolve(authentication);

        assertThat(actor).isEqualTo(new Actor.Operator());
    }

    @Test
    void operatorWinsOverCustomerRole() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("someone")
                .build();
        Authentication authentication = new JwtAuthenticationToken(
                jwt,
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + Roles.CUSTOMER),
                        new SimpleGrantedAuthority("ROLE_" + Roles.OPERATOR)));

        Actor actor = resolver.resolve(authentication);

        assertThat(actor).isEqualTo(new Actor.Operator());
    }

    @Test
    void resolvesCustomerFromClaim() {
        UUID id = UUID.randomUUID();
        Authentication authentication = jwtAuthentication(Roles.CUSTOMER, Map.of(CLAIM, id.toString()));

        Actor actor = resolver.resolve(authentication);

        assertThat(actor).isEqualTo(new Actor.Customer(CustomerId.of(id)));
    }

    @Test
    void deniesCustomerWithoutClaim() {
        Authentication authentication = jwtAuthentication(Roles.CUSTOMER, Map.of());

        assertThatThrownBy(() -> resolver.resolve(authentication)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", ""})
    void deniesCustomerWithMalformedClaim(String malformed) {
        Authentication authentication = jwtAuthentication(Roles.CUSTOMER, Map.of(CLAIM, malformed));

        assertThatThrownBy(() -> resolver.resolve(authentication)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesCustomerWithNonTextClaim() {
        Authentication authentication = jwtAuthentication(Roles.CUSTOMER, Map.of(CLAIM, 42));

        assertThatThrownBy(() -> resolver.resolve(authentication)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesCustomerWhosePrincipalIsNotAJwt() {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + Roles.CUSTOMER)));

        assertThatThrownBy(() -> resolver.resolve(authentication)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesRoleThatOpensNoAccount() {
        Authentication authentication = jwtAuthentication(
                Roles.DATA_PROTECTION_OFFICER, Map.of(CLAIM, UUID.randomUUID().toString()));

        assertThatThrownBy(() -> resolver.resolve(authentication)).isInstanceOf(AccessDeniedException.class);
    }
}
