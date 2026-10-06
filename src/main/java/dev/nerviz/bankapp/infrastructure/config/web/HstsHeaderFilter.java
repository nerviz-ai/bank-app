package dev.nerviz.bankapp.infrastructure.config.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The HSTS writer of a project that has no security filter chain. Norm:
 * {@code .claude/rules/transport-security.md} &sect; HSTS.
 *
 * <p>One writer. With Spring Security on the classpath its {@code HstsHeaderWriter} owns the
 * header, and it writes only when the header is not already present. A filter that stayed
 * would run first and silently override whatever the chain configures — so
 * {@code @ConditionalOnMissingClass} removes this bean the moment the security starter
 * arrives, with nobody having to remember to delete it. Leave the class in place when that
 * happens: it is inert.
 *
 * <p>Secure requests only. Behind an edge {@code isSecure()} is true only when the forwarded
 * headers are honoured ({@code application.yml}'s {@code server.forward-headers-strategy});
 * HSTS on a plain-HTTP response is ignored by browsers anyway. Same value as Spring
 * Security's default, so the header does not change when the owner does. {@code preload} is a
 * recorded decision, never a default — not recorded for this project.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnMissingClass("org.springframework.security.web.SecurityFilterChain")
class HstsHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER = "Strict-Transport-Security";
    private static final String VALUE = "max-age=31536000 ; includeSubDomains";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.isSecure()) {
            response.setHeader(HEADER, VALUE);
        }
        chain.doFilter(request, response);
    }
}
