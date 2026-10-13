package dev.nerviz.bankapp.infrastructure.config.security;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;

/**
 * The WARN line and the counter for every refused authentication and every denied authorization.
 * Authorization events are published only because the bean below exists. Logs the principal's
 * name, method and path — never the token or a header. Counters carry one bounded tag each.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityAuditListener {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditListener.class);

    private final MeterRegistry meters;

    public SecurityAuditListener(MeterRegistry meters) {
        this.meters = meters;
    }

    @Bean
    static AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String category = event.getException().getClass().getSimpleName();
        log.warn("Authentication failed: {}", category);
        meters.counter("security.authentication.failures", "reason", category).increment();
    }

    @EventListener
    public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
        Authentication authentication = event.getAuthentication().get();
        String principal = authentication == null ? "anonymous" : authentication.getName();
        boolean request = event.getObject() instanceof HttpServletRequest;
        String target = event.getObject() instanceof HttpServletRequest http
                ? http.getMethod() + " " + http.getRequestURI()
                : "method";
        log.warn("Access denied for {} at {}", principal, target);
        meters.counter("security.authorization.denied", "kind", request ? "request" : "method")
                .increment();
    }
}
