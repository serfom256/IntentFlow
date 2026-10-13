package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)^```[a-zA-Z]*\\s*(.*?)\\s*```$");

    private static final String GRAMMAR = """
            You translate a user's natural-language request into a single IntentFlow DSL query.
            Reply with the DSL query only: no explanation, no markdown, no code fences.

            DSL grammar:
              <service> [| filter <expr>] [| sort <field> [asc|desc], ...] [| skip <int>] [| take <int>]
            Counting and totals ("how many", "total", "average", "per", "by"):
              <service> [| filter <expr>] [| group <field>, ...] | count|sum <field>|avg <field>|min <field>|max <field>
                        [| sort <group field or the function name> [asc|desc]] [| take <int>]
            - Each stage appears at most once.
            - <expr> combines comparisons with and / or / not and parentheses.
            - Comparisons: <field> == != < <= > >= <value>
                           <field> ~ "text"          (case-insensitive substring match)
                           <field> in (<value>, ...)
                           <field> == null  /  <field> != null
            - Values: "double-quoted text" (no double quotes inside), numbers like 42 or 9.99, true, false, null.
            - Respect field types: text fields take "text", number fields take numbers, boolean fields
              take true/false, timestamp fields take quoted ISO dates like "2026-01-31" or "2026-01-31T10:15:00".
            - For fields with a fixed list of values, use one of the listed values.
            - Only use services and fields listed below. Omit 'take' unless the user asks for a specific count.
            - Text comparisons with == != in are case-insensitive, except for fields marked case-sensitive.
            - A service's relations let filters use the related service's fields as <relation>.<field>,
              e.g. customer.country == "DE". Only filters can use them, not sort or group.
            - If the request needs a service or field that is not listed, or cannot be expressed
              with this grammar, reply exactly: unsupported "<short reason>"
              Never substitute a different field for the one the user asked about.

            Examples:
              books | filter author ~ "tolkien" | sort year desc
              books | filter price < 20 and in_stock == true | take 5
              books | filter genre in ("fantasy", "sci-fi") | sort title
              books | filter author.country == "UK" | sort year desc
              books | group genre | count | sort count desc
              books | filter in_stock == true | avg price
              unsupported "books have no publisher field"
            """;

    private final HttpClient http;
    private final URI endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final Clock clock;

    public OpenAiCompatibleLlmClient(URI baseUrl, String model, String apiKey, Duration timeout) {
        this(baseUrl, model, apiKey, timeout, Clock.systemDefaultZone());
    }

    public OpenAiCompatibleLlmClient(URI baseUrl, String model, String apiKey, Duration timeout, Clock clock) {
        String base = baseUrl.toString();
        this.endpoint = URI.create((base.endsWith("/") ? base : base + "/") + "chat/completions");
        this.model = model;
        this.apiKey = apiKey;
        this.timeout = timeout;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        String body = "{"
                + "\"model\":" + MiniJson.quote(model) + ","
                + "\"temperature\":0,"
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + MiniJson.quote(systemPrompt(catalog, LocalDate.now(clock))) + "},"
                + "{\"role\":\"user\",\"content\":" + MiniJson.quote(normalizedText.value()) + "}"
                + "]}";

        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (!apiKey.isEmpty()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LlmException("LLM request to " + endpoint + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM request interrupted", e);
        }

        if (response.statusCode() / 100 != 2) {
            throw new LlmException("LLM returned HTTP " + response.statusCode() + ": " + response.body());
        }

        String dsl = cleanup(extractContent(response.body()));
        if (dsl.isEmpty()) {
            throw new LlmException("LLM returned an empty answer");
        }
        return DslSources.of(dsl);
    }

    static String systemPrompt(ServiceCatalog catalog, LocalDate today) {
        StringBuilder sb = new StringBuilder(GRAMMAR)
                .append("\nToday is ").append(today).append(".\n")
                .append("\nAvailable services:\n");
        catalog.names().stream()
                .sorted(Comparator.comparing(ServiceName::value))
                .map(catalog::get)
                .forEach(service -> appendService(sb, service));
        return sb.toString();
    }

    private static void appendService(StringBuilder sb, ServiceDescriptor service) {
        sb.append("- ").append(service.name().value());
        if (service.description() != null) {
            sb.append(": ").append(service.description().value());
        }
        sb.append('\n');
        service.fields().stream()
                .filter(FieldMapping::queryable)
                .forEach(f -> sb.append("    ").append(describe(f)).append('\n'));
        for (ServiceRelation relation : service.relations()) {
            sb.append("    relation ").append(relation.name()).append(" -> service ")
                    .append(relation.target().value()).append(" (filter as ").append(relation.name())
                    .append(".<field of ").append(relation.target().value()).append(">)\n");
        }
    }

    private static String describe(FieldMapping f) {
        StringBuilder sb = new StringBuilder(f.logical().value());
        List<String> attrs = new ArrayList<>();
        if (f.type() != FieldType.ANY) {
            attrs.add(f.type().name().toLowerCase(Locale.ROOT));
        }
        if (!f.allowedValues().isEmpty()) {
            attrs.add("one of: " + String.join(", ", f.allowedValues()));
        }
        if (f.caseSensitive()) {
            attrs.add("case-sensitive");
        }
        if (!attrs.isEmpty()) {
            sb.append(" (").append(String.join(", ", attrs)).append(')');
        }
        if (f.description() != null) {
            sb.append(" - ").append(f.description());
        }
        return sb.toString();
    }

    private static String extractContent(String responseBody) {
        try {
            Object root = MiniJson.parse(responseBody);
            Object choices = ((Map<?, ?>) root).get("choices");
            Object first = ((List<?>) choices).getFirst();
            Object message = ((Map<?, ?>) first).get("message");
            Object content = ((Map<?, ?>) message).get("content");
            if (!(content instanceof String s)) {
                throw new LlmException("LLM response has no text content: " + responseBody);
            }
            return s;
        } catch (RuntimeException e) {
            if (e instanceof LlmException le) throw le;
            throw new LlmException("Unexpected LLM response shape: " + responseBody, e);
        }
    }

    static String cleanup(String raw) {
        String s = THINK_BLOCK.matcher(raw).replaceAll("").strip();
        var fence = CODE_FENCE.matcher(s);
        if (fence.matches()) {
            s = fence.group(1).strip();
        }
        return s;
    }
}
