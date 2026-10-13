package dev.nerviz.bankapp.infrastructure.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.DockerClientFactory;

/**
 * Project-wide setup of the filter chain: actuator, API documents, CORS, and the 401/403 bodies the
 * chain writes itself. A slice loads neither the actuator nor the real tracer, so this runs the
 * whole context. Tokens are posted with {@code jwt()}, never decoded by a live provider.
 */
@SpringBootTest(properties = "app.security.cors.allowed-origins=https://app.example.com")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@EnabledIf("dockerAvailable")
class SecurityChainIT {

    private static final String LISTED_ORIGIN = "https://app.example.com";
    private static final String OTHER_ORIGIN = "https://evil.example.com";
    private static final String CUSTOMER_PATH = CustomerController.BASE_PATH + "/00000000-0000-7000-8000-000000000000";

    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor withRole(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void healthAndInfoArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isOk());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @CsvSource({"none,401", "OTHER,403", "OPERATOR,200"})
    void metricsRequireOperator(String role, int expectedStatus) throws Exception {
        var request = get("/actuator/metrics");
        if (!"none".equals(role)) {
            request = request.with(withRole(role));
        }

        mockMvc.perform(request).andExpect(status().is(expectedStatus));
    }

    @Test
    void corsPreflightAllowsListedOriginOnly() throws Exception {
        mockMvc.perform(options(CUSTOMER_PATH)
                        .header(HttpHeaders.ORIGIN, LISTED_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, LISTED_ORIGIN))
                .andExpect(header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                                org.hamcrest.Matchers.containsString("DELETE")));

        mockMvc.perform(options(CUSTOMER_PATH)
                        .header(HttpHeaders.ORIGIN, OTHER_ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void missingTokenIsAProblemDetailsUnauthorizedWithChallenge() throws Exception {
        mockMvc.perform(delete(CUSTOMER_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value(CUSTOMER_PATH))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void missingRoleIsAProblemDetailsForbidden() throws Exception {
        String body = mockMvc.perform(delete(CUSTOMER_PATH).with(withRole("OPERATOR")))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value("Forbidden"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).doesNotContain("DATA_PROTECTION_OFFICER");
    }

    @Test
    void traceIdIsTheRealOneNotThePlaceholder() throws Exception {
        String body =
                mockMvc.perform(delete(CUSTOMER_PATH)).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("unavailable");
    }
}
