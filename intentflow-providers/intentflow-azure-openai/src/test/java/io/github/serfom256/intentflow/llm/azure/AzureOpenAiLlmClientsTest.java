package io.github.serfom256.intentflow.llm.azure;

import com.sun.net.httpserver.HttpServer;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.llm.openai.LlmHttpOptions;
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

class AzureOpenAiLlmClientsTest {

    @IntentService(value = "books", uri = "/books/{{id}}")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private final List<String> seen = new CopyOnWriteArrayList<>();
    private final HttpServer server = server();

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void theDeploymentIsCalledThroughTheV1ApiWithTheKeyHeader() {
        LlmClient llm = AzureOpenAiLlmClients.of(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "gpt-dsl", "secret", Duration.ofSeconds(5), Clock.systemUTC(), LlmHttpOptions.DEFAULTS);

        String dsl = llm.generateDsl(UserTexts.of("books"), ServiceCatalogs.fromAnnotated(Book.class)).value();
        llm.ping();

        assertThat(dsl).isEqualTo("books");
        assertThat(seen).containsExactly("POST /openai/v1/chat/completions api-key=secret model=gpt-dsl",
                "GET /openai/v1/models api-key=secret");
    }

    @Test
    void anEndpointThatAlreadyEndsInV1IsKept() {
        assertThat(AzureOpenAiLlmClients.v1(URI.create("https://res.openai.azure.com/openai/v1/")))
                .hasToString("https://res.openai.azure.com/openai/v1");
        assertThat(AzureOpenAiLlmClients.v1(URI.create("https://res.openai.azure.com/")))
                .hasToString("https://res.openai.azure.com/openai/v1");
    }

    @Test
    void aCustomAuthorizerSendsItsToken() {
        LlmClient llm = AzureOpenAiLlmClients.of(URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "gpt-dsl", request -> request.header("Authorization", "Bearer entra-token"), Duration.ofSeconds(5),
                Clock.systemUTC(), LlmHttpOptions.DEFAULTS);

        llm.ping();

        assertThat(seen).containsExactly("GET /openai/v1/models api-key=null authorization=Bearer entra-token");
    }

    @Test
    void withoutAKeyOrAzureIdentityTheErrorSaysWhatToAdd() {
        assertThatThrownBy(() -> AzureOpenAiLlmClients.of(URI.create("https://res.openai.azure.com"), "gpt-dsl", "",
                Duration.ofSeconds(5), Clock.systemUTC(), LlmHttpOptions.DEFAULTS))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("com.azure:azure-identity");
    }

    private HttpServer server() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/openai/v1/", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath() + " api-key="
                        + exchange.getRequestHeaders().getFirst("api-key")
                        + (auth == null ? "" : " authorization=" + auth)
                        + (body.contains("\"model\":\"gpt-dsl\"") ? " model=gpt-dsl" : ""));
                byte[] reply = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"books\"}}]}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, reply.length);
                exchange.getResponseBody().write(reply);
                exchange.close();
            });
            http.start();
            return http;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
