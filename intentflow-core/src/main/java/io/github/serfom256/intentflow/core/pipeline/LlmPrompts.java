package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LlmPrompts {

    private static final Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern CODE_FENCE = Pattern.compile("(?s)^```[a-zA-Z]*\\s*(.*?)\\s*```$");

    private static final String GRAMMAR = """
            Translate the user's request into one IntentFlow DSL query. Reply with the query only: no explanation,
            no markdown, no code fences.

            Grammar:
              <service> [| filter <expr>] [| top <n> [per <field>, ...] by <field> [asc|desc], ...]
                        [| sort <field> [asc|desc], ...] [| skip <int>] [| take <int>]
              <service> [| filter <expr>] [| group <field>, ...] | count|sum <field>|avg <field>|min <field>|max <field>
                        [| sort <group field or function name> [asc|desc]] [| take <int>]
            - Each stage appears at most once. Use the second form for counts, totals and averages, overall or
              per group ("how many", "total by").
            - top is only for the first rows within each group ("top 3 products per category", "latest order of
              each customer"): it keeps the first <n> rows of each 'per' group in 'by' order and returns whole
              records. It cannot be used with group or count. For the first rows overall use sort and take.
            - <expr>: comparisons combined with and, or, not and parentheses: <field> == != < <= > >= <value>,
              <field> ~ "text" (case-insensitive substring), <field> in (<value>, ...), <field> == null, != null.
            - Values: "text" (no double quotes inside), 42, 9.99, true, false, null, matching the field's type.
            - Any field may also be an expression on the service's own fields: + - * / and parentheses,
              cast(x as number|integer|text|timestamp|date), x::bigint, coalesce(a, b, ...), year(t), month(t),
              day(t), hour(t), date_trunc("month", t), length(s), lower(s), upper(s), round(n), round(n, 2), abs(n).
            - Use only the services, fields and listed values below. Omit take unless the user asks for a number of
              rows ("the 5 cheapest").
            - == != in ignore case unless a field is case-sensitive. Values are stored as listed, usually in
              English: translate names from other languages or scripts, e.g. "Мадрид" -> "Madrid".
            - Keep every condition of the request: "Google phones" filters both brand and category.
            - When a service's description says which records the caller sees, its rows are already limited:
              "my orders" needs no extra filter.
            - If the request needs anything not listed, or cannot be expressed, reply exactly
              unsupported "<short reason>". Never use a different field or drop a condition (such as "my" or
              "near me") to answer a narrower question.
            """;

    private static final String DATES = """
            - Timestamps are quoted ISO dates, "2026-01-31" or "2026-01-31T10:15:00". A date without a time is the
              whole day: == matches any time on it, > means after it, <= includes it.
            """;

    private static final String RELATIONS = """
            - <relation>.<field> filters and sorts by a related service's field, e.g. customer.country == "DE".
              Not in group or expressions.
            """;

    private static final String EXAMPLES = """

            Examples:
              books | filter author ~ "tolkien" | sort year desc
              books | filter genre in ("fantasy", "sci-fi") | sort price | take 5
              books | group genre | count | sort count desc
              books | top 3 per genre by rating desc
              books | filter coalesce(discount, 0) > 10 and year(published_at) == 2025
              books | group month(published_at) | sum price * copies_sold
              unsupported "books have no publisher field"
              unsupported "books are not limited to the caller, so my books cannot be listed"
            """;

    private LlmPrompts() {
    }

    public static String repair(String error) {
        return "That query was rejected: " + error + ". Reply with a corrected query only, "
                + "or unsupported \"<reason>\" if the request cannot be expressed.";
    }

    public static String system(ServiceCatalog catalog, LocalDate today) {
        List<ServiceDescriptor> services = catalog.names().stream().sorted(Comparator.comparing(ServiceName::value))
                .map(catalog::get).toList();
        StringBuilder sb = new StringBuilder(GRAMMAR);
        if (services.stream().flatMap(s -> s.fields().stream()).anyMatch(f -> f.type() == FieldType.TIMESTAMP)) {
            sb.append(DATES);
        }
        if (services.stream().anyMatch(s -> !s.relations().isEmpty())) {
            sb.append(RELATIONS);
        }
        sb.append(EXAMPLES).append("\nAvailable services:\n");
        services.forEach(service -> appendService(sb, service));
        return sb.append("\nToday is ").append(today).append(".\n").toString();
    }

    private static void appendService(StringBuilder sb, ServiceDescriptor service) {
        sb.append("- ").append(service.name().value());
        service.description().ifPresent(description -> sb.append(": ").append(description.value()));
        sb.append('\n');
        service.fields().stream().filter(FieldMapping::queryable)
                .forEach(f -> sb.append("  ").append(describe(f)).append('\n'));
        for (ServiceRelation relation : service.relations()) {
            sb.append("  relation ").append(relation.name()).append(" -> ").append(relation.target().value())
                    .append('\n');
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
        f.description().ifPresent(description -> sb.append(" - ").append(description));
        return sb.toString();
    }

    public static String answer(String raw) {
        String s = THINK_BLOCK.matcher(raw).replaceAll("").strip();
        Matcher fence = CODE_FENCE.matcher(s);
        if (fence.matches()) {
            s = fence.group(1).strip();
        }
        return s;
    }
}
