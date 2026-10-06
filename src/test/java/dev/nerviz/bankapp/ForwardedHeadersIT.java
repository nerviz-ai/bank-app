/*
 * The transport proof of topology A (edge). Norm: `.claude/rules/transport-security.md`
 * § Tests. A real server on a random port: Tomcat's forwarded-header valve runs in the
 * server, so a mock request never exercises it.
 *
 * Two contexts, one per side of the trust list. The trusted one adds loopback to
 * `internal-proxies` — standing in for the edge, since the test's requests come from
 * 127.0.0.1; the other keeps the real `application.yml` value, which leaves loopback
 * out, so the same request is just a client lying about its scheme.
 *
 * h2c: the JDK client asks for HTTP/2 over plain HTTP by `Upgrade: h2c`, which Tomcat
 * accepts on a GET. An edge that speaks h2c with prior knowledge (Caddy `h2c://`) is
 * proven separately, not here — this project has no local Caddy edge.
 *
 * DATASOURCE. The project has a Testcontainers configuration (`persistence-jpa`), so
 * each nested class carries `@Import(TestcontainersConfiguration.class)`: `OVERRIDE`
 * drops the enclosing class's configuration along with the rest, so the import has to
 * be repeated per nested class.
 *
 * Runs in the integration phase (`*IT`, failsafe).
 */
package dev.nerviz.bankapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;

class ForwardedHeadersIT {

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

    static HttpResponse<Void> getAsForwardedHttps(int port) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "203.0.113.7")
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
    }

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @Import(TestcontainersConfiguration.class)
    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = "server.tomcat.remoteip.internal-proxies=127.0.0.0/8, ::1/128")
    @ActiveProfiles("test")
    class FromTrustedProxy {

        @LocalServerPort
        int port;

        @Test
        void requestIsSecureAndHstsIsWritten() throws Exception {
            HttpResponse<Void> response = getAsForwardedHttps(port);

            assertThat(response.headers().firstValue("Strict-Transport-Security"))
                    .isPresent();
        }

        @Test
        void speaksH2cBehindTheEdge() throws Exception {
            HttpResponse<Void> response = getAsForwardedHttps(port);

            assertThat(response.version()).isEqualTo(HttpClient.Version.HTTP_2);
        }
    }

    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @Import(TestcontainersConfiguration.class)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("test")
    class FromAnyoneElse {

        @LocalServerPort
        int port;

        @Test
        void forwardedSchemeIsIgnored() throws Exception {
            HttpResponse<Void> response = getAsForwardedHttps(port);

            assertThat(response.headers().firstValue("Strict-Transport-Security"))
                    .isEmpty();
        }
    }
}
