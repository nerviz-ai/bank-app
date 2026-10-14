package dev.nerviz.bankapp.infrastructure.config.security;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 with a Problem Details body, no reason in {@code detail}. */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    SecurityProblemWriter writer;

    public ProblemDetailAccessDeniedHandler(Tracer tracer) {
        this.writer = new SecurityProblemWriter(tracer);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        writer.write(request, response, HttpStatus.FORBIDDEN);
    }
}
