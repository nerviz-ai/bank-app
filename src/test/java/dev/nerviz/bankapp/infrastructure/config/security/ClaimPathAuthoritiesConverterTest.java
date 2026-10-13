package dev.nerviz.bankapp.infrastructure.config.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ClaimPathAuthoritiesConverterTest {

    private final ClaimPathAuthoritiesConverter converter = new ClaimPathAuthoritiesConverter(
            new TokenAuthoritiesProperties("sub", List.of("roles"), List.of("scope")));

    @Test
    void mapsRolesClaimToRoleAuthorities() {
        Jwt jwt = jwt(Map.of("roles", List.of("DATA_PROTECTION_OFFICER")));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DATA_PROTECTION_OFFICER");
    }

    @Test
    void mapsScopeClaimToScopeAuthorities() {
        Jwt jwt = jwt(Map.of("scope", "read write"));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("SCOPE_read", "SCOPE_write");
    }

    @Test
    void noRolesClaimGivesNoRoleAuthority() {
        Jwt jwt = jwt(Map.of("scope", "read"));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    @Test
    void normalizesRoleToUpperSnakeCase() {
        Jwt jwt = jwt(Map.of("roles", List.of("data-protection-officer")));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_DATA_PROTECTION_OFFICER");
    }

    @Test
    void readsNestedClaimPath() {
        ClaimPathAuthoritiesConverter nested = new ClaimPathAuthoritiesConverter(
                new TokenAuthoritiesProperties("sub", List.of("realm_access.roles"), List.of()));
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("OPERATOR"))));

        assertThat(nested.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OPERATOR");
    }

    @Test
    void pathThroughANonMapYieldsNoAuthority() {
        ClaimPathAuthoritiesConverter nested = new ClaimPathAuthoritiesConverter(
                new TokenAuthoritiesProperties("sub", List.of("realm_access.roles"), List.of()));
        Jwt jwt = jwt(Map.of("realm_access", "not-a-map"));

        assertThat(nested.convert(jwt)).isEmpty();
    }

    @Test
    void claimOfAnUnsupportedTypeYieldsNoAuthority() {
        Jwt jwt = jwt(Map.of("roles", 42));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void blankValuesYieldNoAuthority() {
        Jwt jwt = jwt(Map.of("roles", List.of("", "  ")));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Instant now = Instant.parse("2026-01-15T10:00:00Z");
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user-1")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claims(existing -> existing.putAll(claims))
                .build();
    }
}
