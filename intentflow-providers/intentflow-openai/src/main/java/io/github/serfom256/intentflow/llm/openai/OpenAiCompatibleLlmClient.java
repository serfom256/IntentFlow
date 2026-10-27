package io.github.serfom256.intentflow.llm.openai;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.LlmPrompts;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.ConnectException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public final class OpenAiCompatibleLlmClient implements LlmClient {

    private static final long MAX_RETRY_AFTER = 30;
    private static final Duration PING_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http;
    private final URI endpoint;
    private final URI models;
    private final String model;
    private final RequestAuthorizer authorizer;
    private final Duration timeout;
    private final Clock clock;
    private final LlmHttpOptions options;
    private final @Nullable Double temperature;

    public OpenAiCompatibleLlmClient(URI baseUrl, String model, String apiKey, Duration timeout) {
        this(baseUrl, model, apiKey, timeout, Clock.systemDefaultZone());
    }

    public OpenAiCompatibleLlmClient(URI baseUrl, String model, String apiKey, Duration timeout, Clock clock) {
        this(baseUrl, model, apiKey, timeout, clock, LlmHttpOptions.DEFAULTS);
    }

    public OpenAiCompatibleLlmClient(URI baseUrl, String model, String apiKey, Duration timeout, Clock clock,
            LlmHttpOptions options) {
        this(builder(baseUrl, model).apiKey(apiKey, options.apiKeyHeader()).timeout(timeout).clock(clock)
                .options(options));
    }

    private OpenAiCompatibleLlmClient(Builder builder) {
        String base = builder.baseUrl.toString();
        String root = base.endsWith("/") ? base : base + "/";
        this.endpoint = URI.create(root + "chat/completions");
        this.models = URI.create(root + "models");
        this.model = builder.model;
        this.authorizer = builder.authorizer;
        this.timeout = builder.timeout;
        this.clock = builder.clock;
        this.options = builder.options;
        this.temperature = builder.temperature;
        ProxySelector proxy = options.proxy() != null ? options.proxy() : ProxySelector.getDefault();
        HttpClient.Builder http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        if (proxy != null) {
            http.proxy(proxy);
        }
        if (builder.sslContext != null) {
            http.sslContext(builder.sslContext);
        }
        this.http = http.build();
    }

    public static Builder builder(URI baseUrl, String model) {
        return new Builder(baseUrl, model);
    }

    public static final class Builder {

        private final URI baseUrl;
        private final String model;
        private RequestAuthorizer authorizer = RequestAuthorizer.NONE;
        private Duration timeout = Duration.ofSeconds(60);
        private Clock clock = Clock.systemDefaultZone();
        private LlmHttpOptions options = LlmHttpOptions.DEFAULTS;
        private @Nullable Double temperature = 0d;
        private @Nullable SSLContext sslContext;

        private Builder(URI baseUrl, String model) {
            this.baseUrl = baseUrl;
            this.model = model;
        }

        public Builder apiKey(String apiKey, String header) {
            this.authorizer = RequestAuthorizer.apiKey(apiKey, header);
            return this;
        }

        public Builder authorizer(RequestAuthorizer authorizer) {
            this.authorizer = authorizer;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder options(LlmHttpOptions options) {
            this.options = options;
            return this;
        }

        public Builder temperature(@Nullable Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder sslContext(@Nullable SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public OpenAiCompatibleLlmClient build() {
            return new OpenAiCompatibleLlmClient(this);
        }
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        return complete(catalog, List.of(message("user", normalizedText.value())));
    }

    @Override
    public DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return complete(catalog, List.of(message("user", normalizedText.value()), message("assistant", failed.value()),
                message("user", LlmPrompts.repair(error))));
    }

    @Override
    public void ping() {
        Duration limit = timeout.compareTo(PING_TIMEOUT) < 0 ? timeout : PING_TIMEOUT;
        send(authorized(HttpRequest.newBuilder(models).GET()).timeout(limit).build(), 0);
    }

    private static String message(String role, String content) {
        return "{\"role\":" + MiniJson.quote(role) + ",\"content\":" + MiniJson.quote(content) + "}";
    }

    private DslSource complete(ServiceCatalog catalog, List<String> messages) {
        String body = "{" + "\"model\":" + MiniJson.quote(model) + ","
                + (temperature == null ? "" : "\"temperature\":" + temperature + ",") + "\"messages\":["
                + message("system", LlmPrompts.system(catalog, LocalDate.now(clock)))
                + messages.stream().map(m -> "," + m).collect(Collectors.joining()) + "]}";

        HttpRequest request = authorized(HttpRequest.newBuilder(endpoint).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))).build();
        HttpResponse<String> response = send(request, options.maxRetries());

        Object root;
        String dsl;
        try {
            root = parse(response.body());
            dsl = LlmPrompts.answer(extractContent(root));
        } catch (LlmException e) {
            if (options.logBodies()) {
                log.debug("Unreadable LLM response: {}", abbreviated(response.body()));
            }
            throw e;
        }
        if (dsl.isEmpty()) {
            throw new LlmException("LLM returned an empty answer");
        }
        return DslSources.of(dsl, usage(root),
                root instanceof Map<?, ?> map && map.get("model") instanceof String answered ? answered : model);
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder request) {
        request.timeout(timeout);
        options.headers().forEach(request::header);
        authorizer.authorize(request);
        return request;
    }

    private HttpResponse<String> send(HttpRequest request, int maxRetries) {
        Duration backoff = options.retryBackoff();
        for (int attempt = 1;; attempt++) {
            boolean last = attempt > maxRetries;
            Duration wait = backoff;
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status / 100 == 2) {
                    return response;
                }
                log.debug("LLM returned HTTP {} (attempt {}): {}", status, attempt,
                        options.logBodies() ? abbreviated(response.body()) : response.body().length() + " chars");
                if (last || !retryable(status)) {
                    throw new LlmException(
                            "LLM returned HTTP " + status + (attempt > 1 ? " after " + attempt + " attempts" : ""));
                }
                wait = retryAfter(response).orElse(backoff);
            } catch (HttpConnectTimeoutException | ConnectException e) {
                if (last) {
                    throw new LlmException("LLM at " + request.uri() + " is not reachable: " + reason(e), e);
                }
            } catch (IOException e) {
                throw new LlmException("LLM request to " + request.uri() + " failed: " + reason(e), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("LLM request interrupted", e);
            }
            pause(wait);
            backoff = backoff.multipliedBy(2);
        }
    }

    private static String reason(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static boolean retryable(int status) {
        return status == 408 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                return Optional.of(Duration.ofSeconds(Math.min(Long.parseLong(value.strip()), MAX_RETRY_AFTER)));
            } catch (NumberFormatException notSeconds) {
                return Optional.empty();
            }
        });
    }

    private static void pause(Duration wait) {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM request interrupted", e);
        }
    }

    private static String abbreviated(String text) {
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    private static Object parse(String responseBody) {
        try {
            return MiniJson.parse(responseBody);
        } catch (RuntimeException e) {
            throw new LlmException("LLM response is not JSON", e);
        }
    }

    private static TokenUsage usage(Object root) {
        if (root instanceof Map<?, ?> map && map.get("usage") instanceof Map<?, ?> usage
                && usage.get("prompt_tokens") instanceof Number prompt
                && usage.get("completion_tokens") instanceof Number completion) {
            return new TokenUsage(prompt.longValue(), completion.longValue());
        }
        return TokenUsage.NONE;
    }

    private static String extractContent(Object root) {
        try {
            Object choices = ((Map<?, ?>) root).get("choices");
            Object first = ((List<?>) choices).getFirst();
            Object message = ((Map<?, ?>) first).get("message");
            Object content = ((Map<?, ?>) message).get("content");
            if (!(content instanceof String s)) {
                throw new LlmException("LLM response has no text content");
            }
            return s;
        } catch (RuntimeException e) {
            if (e instanceof LlmException le)
                throw le;
            throw new LlmException("Unexpected LLM response shape", e);
        }
    }
}
