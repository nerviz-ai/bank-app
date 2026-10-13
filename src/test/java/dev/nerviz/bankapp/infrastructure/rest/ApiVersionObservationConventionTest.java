package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.accept.ApiVersionHolder;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.accept.SemanticApiVersionParser;
import org.springframework.web.servlet.HandlerMapping;

/** The {@code api.version} tag stays bounded: the parsed version, or {@code none}. No Spring context. */
class ApiVersionObservationConventionTest {

    private final ApiVersionObservationConvention convention = new ApiVersionObservationConvention();

    @Test
    void tagsTheResolvedVersion() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/customers");
        request.setAttribute(
                HandlerMapping.API_VERSION_ATTRIBUTE,
                ApiVersionHolder.fromVersion(new SemanticApiVersionParser().parseVersion("1")));

        assertThat(versionTagOf(request)).isEqualTo("1.0.0");
    }

    @Test
    void tagsNoneOutsideTheApi() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(versionTagOf(request)).isEqualTo("none");
    }

    @Test
    void tagsNoneForAnUnsupportedVersion() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v99/customers");
        request.setAttribute(
                HandlerMapping.API_VERSION_ATTRIBUTE, ApiVersionHolder.fromError(new InvalidApiVersionException("99")));

        assertThat(versionTagOf(request)).isEqualTo("none");
    }

    private String versionTagOf(MockHttpServletRequest request) {
        ServerRequestObservationContext context =
                new ServerRequestObservationContext(request, new MockHttpServletResponse());
        return convention.getLowCardinalityKeyValues(context).stream()
                .filter(keyValue -> keyValue.getKey().equals("api.version"))
                .map(KeyValue::getValue)
                .findFirst()
                .orElseThrow();
    }
}
