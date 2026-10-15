/*
 * The AsyncAPI contract's gate (.claude/rules/messaging.md § Contract documentation): the served
 * document at /springwolf/docs must equal src/test/asyncapi/asyncapi.snapshot.json. Refresh the
 * snapshot by copying target/asyncapi/asyncapi.current.json in the same change.
 */
package dev.nerviz.bankapp.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nerviz.bankapp.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
class AsyncApiContractIT {

    private static final Path SNAPSHOT = Path.of("src/test/asyncapi/asyncapi.snapshot.json");
    private static final Path CURRENT = Path.of("target/asyncapi/asyncapi.current.json");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    @DisplayName("the served AsyncAPI document is public and equals the committed snapshot")
    void servedDocumentMatchesTheSnapshot() throws Exception {
        String served = mockMvc.perform(MockMvcRequestBuilders.get("/springwolf/docs"))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        JsonNode current = jsonMapper.readTree(served);
        write(current);

        assertThat(current.path("channels").size())
                .as("the document lists no channel at all")
                .isPositive();
        assertThat(SNAPSHOT)
                .as("no snapshot yet — copy %s to %s and commit it", CURRENT, SNAPSHOT)
                .exists();
        JsonNode snapshot = jsonMapper.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));
        assertThat(current)
                .as("the contract changed — copy %s to %s in this same change", CURRENT, SNAPSHOT)
                .isEqualTo(snapshot);
    }

    private void write(JsonNode document) throws IOException {
        Files.createDirectories(CURRENT.getParent());
        Files.writeString(
                CURRENT,
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n",
                StandardCharsets.UTF_8);
    }
}
