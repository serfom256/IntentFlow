package io.github.serfom256.intentflow.core.pipeline;

import com.sun.net.httpserver.HttpServer;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptions;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleLlmClientTest {

    private static final ServiceName BOOKS = ServiceNames.of("books");

    private static final ServiceDescriptor BOOKS_SERVICE = ServiceDescriptors.of(
            BOOKS, ServiceDescriptions.of("Library book catalog"), TableNames.of("book"),
            List.of(
                    FieldMappings.of(LogicalFieldNames.of("title"), ColumnNames.of("title"), true),
                    FieldMappings.of(LogicalFieldNames.of("secret"), ColumnNames.of("secret"), false)),
            List.of(SortKeys.of(LogicalFieldNames.of("title"), SortKey.Direction.ASC)),
            MaxTakes.of(50),
            UriTemplates.of("/books"),
            Object.class);

    private static final ServiceCatalog CATALOG = new ServiceCatalog() {
        @Override
        public Optional<ServiceDescriptor> find(ServiceName name) {
            return BOOKS.equals(name) ? Optional.of(BOOKS_SERVICE) : Optional.empty();
        }

        @Override
        public Set<ServiceName> names() {
            return Set.of(BOOKS);
        }

        @Override
        public int version() {
            return 1;
        }
    };

    private HttpServer server;
    private final AtomicReference<String> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            lastRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = reply.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmClient client(String apiKey) {
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        return LlmClients.openAiCompatible(base, "test-model", apiKey, Duration.ofSeconds(5));
    }

    private static String completion(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"id\":\"x\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\""
                + escaped + "\"},\"finish_reason\":\"stop\"}]}";
    }

    @Test
    void returnsDslFromCompletion() {
        reply = completion("books | filter title ~ \"dune\"");

        String dsl = client("").generateDsl(UserTexts.of("books about dune"), CATALOG).value();

        assertThat(dsl).isEqualTo("books | filter title ~ \"dune\"");
        assertThat(lastAuth.get()).isNull();
    }

    @Test
    void repairShowsTheModelItsAnswerAndTheError() {
        reply = completion("books | filter title ~ \"dune\"");

        String dsl = client("").repairDsl(UserTexts.of("books about dune"), CATALOG,
                DslSources.of("books | filter name ~ \"dune\""), "field 'name' is not declared").value();

        assertThat(dsl).isEqualTo("books | filter title ~ \"dune\"");
        assertThat(lastRequest.get())
                .contains("{\"role\":\"user\",\"content\":\"books about dune\"}")
                .contains("{\"role\":\"assistant\",\"content\":\"books | filter name ~ \\\"dune\\\"\"}")
                .contains("That query was rejected: field 'name' is not declared");
    }

    @Test
    void promptListsServiceAndOnlyQueryableFields() {
        reply = completion("books");

        client("").generateDsl(UserTexts.of("all books"), CATALOG);

        assertThat(lastRequest.get())
                .contains("\"model\":\"test-model\"")
                .contains("- books: Library book catalog")
                .contains("    title\\n")
                .doesNotContain("secret")
                .contains("\"content\":\"all books\"");
    }

    @Test
    void promptDescribesTypesAllowedValuesAndToday() {
        ServiceDescriptor typed = ServiceDescriptors.of(
                BOOKS, null, TableNames.of("book"),
                List.of(
                        FieldMappings.of(LogicalFieldNames.of("price"), ColumnNames.of("price"), true,
                                FieldType.NUMBER, "in USD", List.of()),
                        FieldMappings.of(LogicalFieldNames.of("genre"), ColumnNames.of("genre"), true,
                                FieldType.TEXT, null, List.of("FANTASY", "SCI_FI"))),
                List.of(), MaxTakes.of(5), UriTemplates.of("/books"), Object.class);
        reply = completion("books");
        Clock clock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneOffset.UTC);
        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

        LlmClients.openAiCompatible(base, "m", "", Duration.ofSeconds(5), clock)
                .generateDsl(UserTexts.of("x"), ServiceCatalogs.of(typed));

        assertThat(lastRequest.get())
                .contains("Today is 2026-10-11.")
                .contains("price (number) - in USD")
                .contains("genre (text, one of: FANTASY, SCI_FI)");
    }

    @Test
    void stripsThinkBlocksAndCodeFences() {
        reply = completion("<think>\nthe user wants books\n</think>\n```dsl\nbooks | take 3\n```");

        String dsl = client("").generateDsl(UserTexts.of("three books"), CATALOG).value();

        assertThat(dsl).isEqualTo("books | take 3");
    }

    @Test
    void sendsBearerTokenWhenApiKeyGiven() {
        reply = completion("books");

        client("sk-test").generateDsl(UserTexts.of("all books"), CATALOG);

        assertThat(lastAuth.get()).isEqualTo("Bearer sk-test");
    }

    @Test
    void nonSuccessStatusBecomesLlmException() {
        status = 500;
        reply = "{\"error\":\"model not loaded\"}";

        assertThatThrownBy(() -> client("").generateDsl(UserTexts.of("x"), CATALOG))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("model not loaded");
    }

    @Test
    void malformedResponseBecomesLlmException() {
        reply = "{\"choices\":[]}";

        assertThatThrownBy(() -> client("").generateDsl(UserTexts.of("x"), CATALOG))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Unexpected LLM response shape");
    }

    @Test
    void emptyAnswerBecomesLlmException() {
        reply = completion("<think>hmm</think>   ");

        assertThatThrownBy(() -> client("").generateDsl(UserTexts.of("x"), CATALOG))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("empty answer");
    }
}
