package io.github.serfom256.intentflow.llm.bedrock;

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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

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

class BedrockLlmClientTest {

    @IntentService(value = "books", uri = "/books/{{id}}")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);
    private static final String MODEL = "us.anthropic.claude-opus-5-5";
    private static final String ANSWER = "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":"
            + "[{\"text\":\"books | take 1\"}]}},\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":40,"
            + "\"outputTokens\":7,\"totalTokens\":947,\"cacheReadInputTokens\":900},\"metrics\":{\"latencyMs\":120}}";

    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile int status = 200;
    private final HttpServer server = server();

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmClient client(BedrockSettings settings) {
        return BedrockLlmClients.of(MODEL, settings,
                StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIDTEST", "secret")),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()), Duration.ofSeconds(5), 0,
                Clock.systemUTC());
    }

    @Test
    void theConverseApiGetsTheCatalogAsSystemContent() {
        DslSource dsl = client(BedrockSettings.of("us-east-1")).generateDsl(UserTexts.of("one book"), CATALOG);

        assertThat(dsl.value()).isEqualTo("books | take 1");
        assertThat(dsl.model()).isEqualTo(MODEL);
        assertThat(dsl.usage()).isEqualTo(new TokenUsage(940, 7));
        assertThat(requests.getFirst()).startsWith("POST /model/" + MODEL + "/converse signed=true ")
                .contains("Available services:").contains("\"maxTokens\":16000")
                .doesNotContain("temperature").doesNotContain("cachePoint");
    }

    @Test
    void temperatureAndPromptCachingCanBeTurnedOn() {
        client(new BedrockSettings("us-east-1", 2000, 0f, true)).generateDsl(UserTexts.of("books"), CATALOG);

        assertThat(requests.getFirst()).contains("\"temperature\":0").contains("\"cachePoint\":{\"type\":\"default\"}")
                .contains("\"maxTokens\":2000");
    }

    @Test
    void aRepairSendsTheAssistantTurnAndTheError() {
        client(BedrockSettings.of("us-east-1")).repairDsl(UserTexts.of("books by x"), CATALOG,
                DslSources.of("books | filter author == \"x\""), "field 'author' is not declared");

        assertThat(requests.getFirst()).contains("\"role\":\"assistant\"")
                .contains("That query was rejected: field 'author' is not declared");
    }

    @Test
    void aThrottledRequestIsAnLlmException() {
        status = 429;

        assertThatThrownBy(() -> client(BedrockSettings.of("us-east-1")).generateDsl(UserTexts.of("books"), CATALOG))
                .isInstanceOf(LlmException.class).hasMessageStartingWith("Bedrock request failed");
    }

    @Test
    void pingChecksTheCredentials() {
        client(BedrockSettings.of("us-east-1")).ping();

        assertThat(requests).isEmpty();
    }

    private HttpServer server() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/model/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " signed="
                        + (auth != null && auth.startsWith("AWS4-HMAC-SHA256")) + " " + body);
                byte[] out = (status == 200 ? ANSWER : "{\"message\":\"Too many requests\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                if (status != 200) {
                    exchange.getResponseHeaders().add("x-amzn-ErrorType", "ThrottlingException");
                }
                exchange.sendResponseHeaders(status, out.length);
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
