package dev.nerviz.bankapp.infrastructure.config.security;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 with {@code WWW-Authenticate: Bearer} and a Problem Details body, no reason in {@code detail}. */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    SecurityProblemWriter writer;

    public ProblemDetailAuthenticationEntryPoint(Tracer tracer) {
        this.writer = new SecurityProblemWriter(tracer);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        writer.write(request, response, HttpStatus.UNAUTHORIZED);
    }
}
