package dev.nerviz.bankapp.infrastructure.config.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * The one way this project's callers prove who they are. Exactly one bean per filter chain.
 * {@code challengeScheme()} is the scheme a 401 names in {@code WWW-Authenticate}.
 */
public interface AuthenticationMechanism {

    void applyTo(
            HttpSecurity http,
            ProblemDetailAuthenticationEntryPoint entryPoint,
            ProblemDetailAccessDeniedHandler accessDeniedHandler);

    String challengeScheme();
}
