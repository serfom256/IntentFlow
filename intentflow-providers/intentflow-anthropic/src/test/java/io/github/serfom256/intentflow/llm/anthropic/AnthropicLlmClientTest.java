package io.github.serfom256.intentflow.llm.anthropic;

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

class AnthropicLlmClientTest {

    @IntentService(value = "books", uri = "/books/{{id}}")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);
    private static final String ANSWER = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
            + "\"model\":\"claude-opus-5-5\",\"content\":[{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"s\"},"
            + "{\"type\":\"text\",\"text\":\"books | take 1\"}],\"stop_reason\":\"end_turn\",\"stop_sequence\":null,"
            + "\"usage\":{\"input_tokens\":12,\"output_tokens\":30,\"cache_read_input_tokens\":900,"
            + "\"cache_creation_input_tokens\":0}}";
    private static final String REFUSAL = "{\"id\":\"msg_2\",\"type\":\"message\",\"role\":\"assistant\","
            + "\"model\":\"claude-opus-5-5\",\"content\":[],\"stop_reason\":\"refusal\",\"stop_sequence\":null,"
            + "\"stop_details\":{\"type\":\"refusal\",\"category\":\"cyber\",\"explanation\":\"x\"},"
            + "\"usage\":{\"input_tokens\":12,\"output_tokens\":0}}";

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile String reply = ANSWER;
    private final HttpServer server = server();

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmClient client(AnthropicSettings settings) {
        return AnthropicLlmClients.of(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "claude-opus-5-5", "sk-test", settings, Duration.ofSeconds(5), 0, null, Clock.systemUTC());
    }

    @Test
    void theCatalogPromptIsACachedSystemBlockAndTheAnswerIsTheText() {
        DslSource dsl = client(AnthropicSettings.defaults()).generateDsl(UserTexts.of("one book"), CATALOG);

        assertThat(dsl.value()).isEqualTo("books | take 1");
        assertThat(dsl.model()).isEqualTo("claude-opus-5-5");
        assertThat(dsl.usage()).isEqualTo(new TokenUsage(912, 30));
        String request = requests.getFirst();
        assertThat(request).startsWith("POST /v1/messages x-api-key=sk-test version=true "
                + "beta=server-side-fallback-2026-07-01 ")
                .contains("\"cache_control\":{\"type\":\"ephemeral\"}")
                .contains("\"max_tokens\":16000")
                .contains("\"fallbacks\":\"default\"")
                .contains("Available services:")
                .doesNotContain("temperature");
    }

    @Test
    void aRepairSendsTheRejectedQueryAndTheError() {
        client(AnthropicSettings.defaults()).repairDsl(UserTexts.of("books by x"), CATALOG,
                DslSources.of("books | filter author == \"x\""), "field 'author' is not declared");

        assertThat(requests.getFirst()).contains("\"role\":\"assistant\"")
                .contains("That query was rejected: field 'author' is not declared");
    }

    @Test
    void effortAndNoFallbackCanBeConfigured() {
        client(new AnthropicSettings(4000, "low", false)).generateDsl(UserTexts.of("books"), CATALOG);

        assertThat(requests.getFirst()).contains("\"effort\":\"low\"").contains("\"max_tokens\":4000")
                .doesNotContain("fallbacks").contains(" beta=null ");
    }

    @Test
    void aRefusalIsAnLlmExceptionWithItsCategory() {
        reply = REFUSAL;

        assertThatThrownBy(() -> client(AnthropicSettings.defaults()).generateDsl(UserTexts.of("books"), CATALOG))
                .isInstanceOf(LlmException.class)
                .hasMessage("Claude declined to translate the question (cyber)");
    }

    @Test
    void pingRetrievesTheModel() {
        client(AnthropicSettings.defaults()).ping();

        assertThat(requests).singleElement().asString().startsWith("GET /v1/models/claude-opus-5-5");
    }

    private HttpServer server() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/v1/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " x-api-key="
                        + exchange.getRequestHeaders().getFirst("x-api-key") + " version="
                        + (exchange.getRequestHeaders().getFirst("anthropic-version") != null) + " beta="
                        + exchange.getRequestHeaders().getFirst("anthropic-beta") + " " + body);
                byte[] out = (exchange.getRequestURI().getPath().startsWith("/v1/models")
                        ? "{\"type\":\"model\",\"id\":\"claude-opus-5-5\",\"display_name\":\"Claude Opus 5.5\","
                                + "\"created_at\":\"2026-01-01T00:00:00Z\"}"
                        : reply).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
            });
            http.start();
            return http;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
