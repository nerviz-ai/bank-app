package dev.nerviz.bankapp.infrastructure.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.accept.ApiVersionDeprecationHandler;
import org.springframework.web.accept.StandardApiVersionDeprecationHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * The project's one API versioning configuration ({@.claude/rules/api-rest.md} § Versioning).
 * Lives in the REST adapter's root package — its own package is what scopes the prefix. No
 * {@code spring.mvc.apiversion.*} property in {@code application.yml}: Boot would register a
 * second resolver beside this one, so the supported list has one writer.
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersioningConfig implements WebMvcConfigurer {

    /** The path segment that holds the version: {@code /api} is segment 0, {@code v1} segment 1. */
    private static final int VERSION_SEGMENT = 1;

    /**
     * Every controller of this package and its subpackages, and no other — springdoc's own
     * {@code @RestController} keeps its path, so {@code /v3/api-docs} still answers.
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(
                "/api/v{version}",
                HandlerTypePredicate.forAnnotation(RestController.class)
                        .and(HandlerTypePredicate.forBasePackageClass(ApiVersioningConfig.class)));
    }

    /**
     * The version is read only under {@code /api/}: {@code /actuator}, {@code /v3/api-docs} and
     * {@code /swagger-ui} are never parsed as one. No default and an explicit supported list —
     * removing a version from {@code addSupportedVersions} is its sunset: 400 from then on. Not
     * "required" as a flag: a controller outside {@code /api/} ({@code /springwolf/docs}) has no
     * version to resolve and would answer 400. Under {@code /api/} the segment is still always
     * read and checked against the supported list, so a missing or unknown version is still 400.
     */
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .usePathSegment(
                        VERSION_SEGMENT,
                        path -> path.pathWithinApplication().value().startsWith("/api/"))
                .setVersionRequired(false)
                .detectSupportedVersions(false)
                .addSupportedVersions("1");
    }

    /**
     * One entry per deprecated version, both dates and the deprecation link — empty until the
     * first deprecation. Boot injects this bean into the versioning above on its own.
     */
    @Bean
    ApiVersionDeprecationHandler apiVersionDeprecationHandler() {
        return new StandardApiVersionDeprecationHandler();
    }
}
