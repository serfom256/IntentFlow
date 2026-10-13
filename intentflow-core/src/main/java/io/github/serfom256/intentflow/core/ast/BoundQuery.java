package io.github.serfom256.intentflow.core.ast;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Generated SQL split around its bind placeholders: {@code segments().size()}
 * is always {@code parameters().size() + 1}, and parameter {@code i} sits
 * between segment {@code i} and {@code i + 1}. Executors render the
 * placeholder syntax their engine expects: JDBC {@code ?}, JPA {@code ?1},
 * OpenMapper {@code [p1]}.
 */
public interface BoundQuery {

    List<String> segments();

    List<DslLiteral> parameters();

    /** Joins the segments, asking {@code placeholder} for the text of each parameter (1-based). */
    default String render(IntFunction<String> placeholder) {
        StringBuilder sb = new StringBuilder(segments().getFirst());
        for (int i = 1; i < segments().size(); i++) {
            sb.append(placeholder.apply(i)).append(segments().get(i));
        }
        return sb.toString();
    }

    /** JDBC form with {@code ?} placeholders. */
    default String sql() {
        return render(i -> "?");
    }

    /** Parameter values as plain Java objects: {@code String}, {@code BigDecimal}, {@code Boolean} or {@code null}. */
    default List<@Nullable Object> values() {
        List<@Nullable Object> values = new ArrayList<>(parameters().size());
        for (DslLiteral p : parameters()) {
            values.add(switch (p) {
                case TextLiteral t -> t.value();
                case NumberLiteral n -> n.value();
                case BoolLiteral b -> b.value();
                case NullLiteral n -> null;
            });
        }
        return values;
    }
}
