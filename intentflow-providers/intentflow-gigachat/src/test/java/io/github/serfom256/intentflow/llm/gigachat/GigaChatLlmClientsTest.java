package io.github.serfom256.intentflow.llm.gigachat;

import com.sun.net.httpserver.HttpServer;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.llm.openai.LlmHttpOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GigaChatLlmClientsTest {

    @IntentService(value = "books", uri = "/books/{{id}}")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);

    private final MovableClock clock = new MovableClock();
    private final AtomicInteger tokensIssued = new AtomicInteger();
    private final List<String> oauthRequests = new CopyOnWriteArrayList<>();
    private final List<String> chatRequests = new CopyOnWriteArrayList<>();
    private volatile int oauthStatus = 200;
    private final HttpServer server = server();

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-21T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private LlmClient client() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        GigaChatSettings settings = new GigaChatSettings(URI.create(base + "/api/v1"),
                URI.create(base + "/api/v2/oauth"), "GIGACHAT_API_CORP", null);
        return GigaChatLlmClients.of("GigaChat-2-Max", "Y2xpZW50OnNlY3JldA==", settings, Duration.ofSeconds(5), clock,
                LlmHttpOptions.DEFAULTS);
    }

    @Test
    void theAuthorizationKeyIsExchangedForAnAccessTokenThatIsReused() {
        LlmClient llm = client();

        assertThat(llm.generateDsl(UserTexts.of("books"), CATALOG).value()).isEqualTo("books");
        llm.generateDsl(UserTexts.of("books"), CATALOG);

        assertThat(oauthRequests).singleElement().asString()
                .contains("Basic Y2xpZW50OnNlY3JldA==").contains("scope=GIGACHAT_API_CORP").contains("rquid=true");
        assertThat(chatRequests).hasSize(2).allSatisfy(request -> assertThat(request)
                .startsWith("Bearer token-1").contains("\"model\":\"GigaChat-2-Max\"")
                .doesNotContain("temperature"));
    }

    @Test
    void anExpiringTokenIsRenewed() {
        LlmClient llm = client();
        llm.generateDsl(UserTexts.of("books"), CATALOG);
        clock.advance(Duration.ofMinutes(29).plusSeconds(30));

        llm.generateDsl(UserTexts.of("books"), CATALOG);

        assertThat(tokensIssued).hasValue(2);
        assertThat(chatRequests.getLast()).startsWith("Bearer token-2");
    }

    @Test
    void aRejectedKeyIsAnLlmException() {
        oauthStatus = 401;

        assertThatThrownBy(() -> client().generateDsl(UserTexts.of("books"), CATALOG))
                .isInstanceOf(LlmException.class)
                .hasMessage("GigaChat OAuth returned HTTP 401");
    }

    private HttpServer server() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.createContext("/api/v2/oauth", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                oauthRequests.add(exchange.getRequestHeaders().getFirst("Authorization") + " " + body + " rquid="
                        + (exchange.getRequestHeaders().getFirst("RqUID") != null));
                byte[] reply = oauthStatus != 200 ? new byte[0]
                        : ("{\"access_token\":\"token-" + tokensIssued.incrementAndGet() + "\",\"expires_at\":"
                                + (clock.millis() + Duration.ofMinutes(30).toMillis()) + "}")
                                .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(oauthStatus, reply.length == 0 ? -1 : reply.length);
                exchange.getResponseBody().write(reply);
                exchange.close();
            });
            http.createContext("/api/v1/chat/completions", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                chatRequests.add(exchange.getRequestHeaders().getFirst("Authorization") + " " + body);
                byte[] reply = ("{\"model\":\"GigaChat-2-Max:2.0\",\"choices\":[{\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"books\"}}],\"usage\":{\"prompt_tokens\":500,\"completion_tokens\":3}}")
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
