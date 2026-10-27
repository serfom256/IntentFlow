package io.github.serfom256.intentflow.llm.vertex;

import com.sun.net.httpserver.HttpServer;
import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiLlmClientTest {

    @IntentService(value = "books", uri = "/books/{{id}}")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);
    private static final String ANSWER = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":"
            + "[{\"text\":\"```\\nbooks | take 1\\n```\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":"
            + "{\"promptTokenCount\":800,\"candidatesTokenCount\":6,\"thoughtsTokenCount\":20},"
            + "\"modelVersion\":\"gemini-test-001\"}";

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private final HttpServer server = server();

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmClient client() {
        return GeminiLlmClients.apiKey(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "key-1",
                "gemini-test", 16000, Duration.ofSeconds(5), Clock.systemUTC());
    }

    @Test
    void theCatalogIsTheSystemInstructionAndTheAnswerIsTheText() {
        DslSource dsl = client().generateDsl(UserTexts.of("one book"), CATALOG);

        assertThat(dsl.value()).isEqualTo("books | take 1");
        assertThat(dsl.model()).hasValue("gemini-test-001");
        assertThat(dsl.usage()).isEqualTo(new TokenUsage(800, 26));
        assertThat(requests.getFirst()).startsWith("POST /v1beta/models/gemini-test:generateContent key=key-1 ")
                .contains("\"systemInstruction\"").contains("Available services:").contains("\"temperature\":0")
                .contains("\"maxOutputTokens\":16000");
    }

    @Test
    void aRepairSendsTheModelTurnAndTheError() {
        client().repairDsl(UserTexts.of("books by x"), CATALOG, DslSources.of("books | filter author == \"x\""),
                "field 'author' is not declared");

        assertThat(requests.getFirst()).contains("\"role\":\"model\"")
                .contains("That query was rejected: field 'author' is not declared");
    }

    @Test
    void anApiErrorIsAnLlmException() {
        status = 403;

        assertThatThrownBy(() -> client().generateDsl(UserTexts.of("books"), CATALOG)).isInstanceOf(LlmException.class)
                .hasMessageStartingWith("Gemini request failed");
    }

    @Test
    void pingReadsTheModel() {
        client().ping();

        assertThat(requests).singleElement().asString().startsWith("GET /v1beta/models/gemini-test key=key-1");
    }

    private HttpServer server() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/v1beta/models/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " key="
                        + exchange.getRequestHeaders().getFirst("x-goog-api-key") + " " + body);
                String out = status != 200
                        ? "{\"error\":{\"code\":403,\"message\":\"denied\",\"status\":" + "\"PERMISSION_DENIED\"}}"
                        : exchange.getRequestMethod().equals("GET") ? "{\"name\":\"models/gemini-test\"}" : ANSWER;
                byte[] bytes = out.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            http.start();
            return http;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
