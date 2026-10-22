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
            You translate a user's natural-language request into a single IntentFlow DSL query.
            Reply with the DSL query only: no explanation, no markdown, no code fences.

            DSL grammar:
              <service> [| filter <expr>] [| top <n> [per <field>, ...] by <field> [asc|desc], ...]
                        [| sort <field> [asc|desc], ...] [| skip <int>] [| take <int>]
            Best or latest rows within each group ("top 3 products per category", "latest order of each
            customer") use top: it keeps the first <n> rows of every 'per' group in the 'by' order and still
            returns whole records. It cannot be combined with group or count/sum/avg/min/max.
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
            - Wherever a field can appear (filter, sort, group, sum/avg/min/max) you may also use an expression:
                arithmetic + - * / and parentheses, casts cast(x as number|integer|text|timestamp|date) or x::bigint,
                coalesce(a, b, ...), year(t), month(t), day(t), hour(t), date_trunc("month", t),
                length(s), lower(s), upper(s), round(n), round(n, 2), abs(n).
              Use expressions only on fields of the service itself, not on relation fields.
            - Respect field types: text fields take "text", number fields take numbers, boolean fields
              take true/false, timestamp fields take quoted ISO dates like "2026-01-31" or "2026-01-31T10:15:00".
            - A date without a time means that whole day: created_at == "2026-10-10" matches any time on that
              day, > "2026-10-10" means after it and <= "2026-10-10" includes it.
            - For fields with a fixed list of values, use one of the listed values.
            - Compare text with values as they are stored, which is usually English: translate names the user
              writes in another language or script, e.g. "Мадрид" -> "Madrid", "東京" -> "Tokyo".
            - Every condition in the request becomes part of the filter: "Google phones" is the brand Google and
              the category of phones, not only the brand.
            - Only use services and fields listed below. Omit 'take' unless the user asks for a specific count.
            - Text comparisons with == != in are case-insensitive, except for fields marked case-sensitive.
            - A service's relations let filters use the related service's fields as <relation>.<field>,
              e.g. customer.country == "DE", and sort by them, e.g. sort customer.name. Not group by them.
            - If the request needs a service or field that is not listed, or cannot be expressed
              with this grammar, reply exactly: unsupported "<short reason>"
              Never substitute a different field for the one the user asked about, and never answer a
              narrower question by dropping a condition you cannot express (for example "my", "near me",
              or a field that is not listed): reply unsupported instead.
            - When a service's description says which records the caller sees, its rows are already limited
              to them: answer "my orders" with that service and no extra filter.

            Examples:
              books | filter author ~ "tolkien" | sort year desc
              books | filter price < 20 and in_stock == true | take 5
              books | filter genre in ("fantasy", "sci-fi") | sort title
              books | filter author.country == "UK" | sort year desc
              books | group genre | count | sort count desc
              books | top 3 per genre by rating desc | sort genre
              books | filter in_stock == true | avg price
              books | filter coalesce(discount, 0) > 10 and year(published_at) == 2025
              books | group month(published_at) | sum price * copies_sold
              unsupported "books have no publisher field"
            """;

    private LlmPrompts() {
    }

    public static String repair(String error) {
        return "That query was rejected: " + error + ". Reply with a corrected query only, "
                + "or unsupported \"<reason>\" if the request cannot be expressed.";
    }

    public static String system(ServiceCatalog catalog, LocalDate today) {
        StringBuilder sb = new StringBuilder(GRAMMAR).append("\nAvailable services:\n");
        catalog.names().stream()
                .sorted(Comparator.comparing(ServiceName::value))
                .map(catalog::get)
                .forEach(service -> appendService(sb, service));
        return sb.append("\nToday is ").append(today).append(".\n").toString();
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

    public static String answer(String raw) {
        String s = THINK_BLOCK.matcher(raw).replaceAll("").strip();
        Matcher fence = CODE_FENCE.matcher(s);
        if (fence.matches()) {
            s = fence.group(1).strip();
        }
        return s;
    }
}
