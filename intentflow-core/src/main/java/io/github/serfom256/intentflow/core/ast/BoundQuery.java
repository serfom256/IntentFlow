package io.github.serfom256.intentflow.core.ast;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Generated SQL split around its bind placeholders: {@code segments().size()}
 * is always {@code parameters().size() + 1}, and parameter {@code i} sits
 * between segment {@code i} and {@code i + 1}. Executors render the placeholder
 * syntax their engine expects: JDBC {@code ?}, JPA {@code ?1}, OpenMapper
 * {@code [p1]}.
 */
public interface BoundQuery {

    List<String> segments();

    List<DslLiteral> parameters();

    /**
     * Joins the segments, asking {@code placeholder} for the text of each parameter
     * (1-based).
     */
    default String render(IntFunction<String> placeholder) {
        StringBuilder sb = new StringBuilder(segments().getFirst());
        for (int i = 1; i < segments().size(); i++) {
            sb.append(placeholder.apply(i)).append(segments().get(i));
        }
        return sb.toString();
    }

    /** JDBC form with {@code ?} placeholders. */
    /**
     * Names of the optimizations applied while building the statement, for traces
     * and debugging.
     */
    default List<String> optimizations() {
        return List.of();
    }

    default String sql() {
        return render(i -> "?");
    }

    /**
     * Parameter values as plain Java objects: {@code String}, {@code Long} for a
     * whole number, {@code BigDecimal} for any other number, {@code Boolean} or
     * {@code null}. Whole numbers bind as {@code Long} so that PostgreSQL compares
     * an integer column without casting it to {@code numeric}, which would keep it
     * from using its index.
     */
    default List<@Nullable Object> values() {
        List<@Nullable Object> values = new ArrayList<>(parameters().size());
        for (DslLiteral p : parameters()) {
            values.add(switch (p) {
            case TextLiteral t -> t.value();
            case NumberLiteral n -> number(n.value());
            case BoolLiteral b -> b.value();
            case NullLiteral n -> null;
            });
        }
        return values;
    }

    private static Object number(BigDecimal value) {
        return value.scale() <= 0 && value.precision() - value.scale() < 19 ? value.longValueExact() : value;
    }
}
