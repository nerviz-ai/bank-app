package dev.nerviz.bankapp.infrastructure.config.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Resource server, signed token. Boot builds the {@code JwtDecoder} from
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} and adds the audience check from
 * {@code ...jwt.audiences}; declaring a decoder bean here would silently drop that check.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TokenAuthoritiesProperties.class)
public class JwtResourceServerConfig {

    @Bean
    AuthenticationMechanism jwtResourceServer(TokenAuthoritiesProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName(properties.principalClaim());
        converter.setJwtGrantedAuthoritiesConverter(new ClaimPathAuthoritiesConverter(properties));
        return new AuthenticationMechanism() {
            @Override
            public void applyTo(
                    HttpSecurity http,
                    ProblemDetailAuthenticationEntryPoint entryPoint,
                    ProblemDetailAccessDeniedHandler accessDeniedHandler) {
                http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
            }

            @Override
            public String challengeScheme() {
                return "Bearer";
            }
        };
    }
}
