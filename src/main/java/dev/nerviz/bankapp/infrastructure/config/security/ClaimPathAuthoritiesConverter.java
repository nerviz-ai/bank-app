package dev.nerviz.bankapp.infrastructure.config.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Reads every configured claim path and prefixes each value by its kind: roles {@code ROLE_},
 * scopes {@code SCOPE_}. A path that is absent, or holds something that is neither a list nor
 * a space-separated string, yields no authority — never an error: a token without roles is a
 * caller without roles.
 */
public final class ClaimPathAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final TokenAuthoritiesProperties properties;

    public ClaimPathAuthoritiesConverter(TokenAuthoritiesProperties properties) {
        this.properties = properties;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> claims = jwt.getClaims();
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        authorities.addAll(authorities(claims, properties.rolesClaims(), role -> "ROLE_" + normalize(role)));
        authorities.addAll(authorities(claims, properties.scopesClaims(), scope -> "SCOPE_" + scope));
        return authorities;
    }

    private static List<GrantedAuthority> authorities(
            Map<String, Object> claims, List<String> paths, UnaryOperator<String> toAuthority) {
        return paths.stream()
                .flatMap(path -> values(read(claims, path)))
                .filter(value -> !value.isBlank())
                .<GrantedAuthority>map(value -> new SimpleGrantedAuthority(toAuthority.apply(value)))
                .toList();
    }

    private static Object read(Map<String, Object> claims, String path) {
        Object current = claims;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static Stream<String> values(Object claim) {
        if (claim instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf);
        }
        if (claim instanceof String text) {
            return Arrays.stream(text.trim().split("\\s+"));
        }
        return Stream.empty();
    }

    /** Roles become {@code UPPER_SNAKE_CASE} constants; scopes are case-sensitive and left as issued. */
    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
