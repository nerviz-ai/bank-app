package dev.nerviz.bankapp.infrastructure.config.security;

import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The project's single servlet filter chain, deny by default. Request rules run specific to
 * general and end in {@code anyRequest().authenticated()}. The default headers stay on, HSTS
 * included: this chain is the one writer of the header, and {@code HstsHeaderFilter} stands
 * aside by itself. CSRF is off only because no cookie authenticates a request.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityAccessProperties.class)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

    private static final String[] API_DOCS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};
    private static final String CUSTOMERS = "/api/*/customers";

    AuthenticationMechanism mechanism;
    ProblemDetailAuthenticationEntryPoint entryPoint;
    ProblemDetailAccessDeniedHandler accessDeniedHandler;
    SecurityAccessProperties access;

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .headers(Customizer.withDefaults())
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(this::requestRules);
        mechanism.applyTo(http, entryPoint, accessDeniedHandler);
        return http.build();
    }

    /**
     * Specific to general, every path naming all API versions of its route
     * ({@.claude/rules/authorization.md} § Default access). {@code POST /api/*}{@code /customers}
     * is public by UC-001's decision, kept in UC-006 ({@code 32-security.md} § 1).
     */
    private void requestRules(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry requests) {
        if (access.apiDocsPublic()) {
            requests.requestMatchers(API_DOCS).permitAll();
        }
        requests.requestMatchers(EndpointRequest.to("health", "info")).permitAll();
        requests.requestMatchers(EndpointRequest.toAnyEndpoint()).hasRole(access.actuatorRole());
        requests.requestMatchers(HttpMethod.POST, CUSTOMERS).permitAll();
        requests.requestMatchers(HttpMethod.DELETE, CUSTOMERS + "/*").hasRole(Roles.DATA_PROTECTION_OFFICER);
        requests.anyRequest().authenticated();
    }

    /**
     * Typed as {@code UrlBasedCorsConfigurationSource} on purpose: it is the type the framework
     * looks up, and a second {@code CorsConfigurationSource} bean anywhere disables the lookup.
     * An empty origin list registers no mapping at all.
     */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (access.cors().allowedOrigins().isEmpty()) {
            return source;
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(access.cors().allowedOrigins());
        configuration.setAllowedMethods(access.cors().allowedMethods());
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location", "WWW-Authenticate"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(access.cors().maxAge());
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
