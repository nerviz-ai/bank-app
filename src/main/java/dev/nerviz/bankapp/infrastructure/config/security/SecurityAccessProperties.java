package dev.nerviz.bankapp.infrastructure.config.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code app.security.*} — the project-wide access facts. Defaults are the rule's defaults. */
@ConfigurationProperties("app.security")
public record SecurityAccessProperties(
        @DefaultValue("true") boolean apiDocsPublic,
        @DefaultValue("OPERATOR") String actuatorRole,
        @DefaultValue Cors cors) {

    /** Browser origins allowed to call {@code /api/**}. Empty means no mapping at all. */
    public record Cors(
            @DefaultValue List<String> allowedOrigins,
            @DefaultValue({"GET", "POST", "DELETE"}) List<String> allowedMethods,
            @DefaultValue("PT30M") Duration maxAge) {}
}
