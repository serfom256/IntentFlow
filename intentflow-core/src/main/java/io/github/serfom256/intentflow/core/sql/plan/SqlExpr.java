package io.github.serfom256.intentflow.core.sql.plan;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.List;

/**
 * A value in a {@link Select}: a column, a bound parameter or a computation
 * over them. Nodes say what is computed; the renderer and the
 * {@code SqlDialect} decide how it is spelled.
 */
public sealed interface SqlExpr {

    record Column(@Nullable String qualifier, String name) implements SqlExpr {
    }

    record Param(DslLiteral value) implements SqlExpr {
    }

    record TimestampParam(DslLiteral value, @Nullable Object bound) implements SqlExpr {

        public TimestampParam(DslLiteral value) {
            this(value, null);
        }
    }

    record InZone(SqlExpr value, ZoneId zone) implements SqlExpr {
    }

    record Value(Object value) implements SqlExpr {
    }

    record Null() implements SqlExpr {
    }

    record Inline(long value) implements SqlExpr {
    }

    record Call(String function, List<SqlExpr> args) implements SqlExpr {

        public Call {
            args = List.copyOf(args);
        }

        public Call(String function, SqlExpr... args) {
            this(function, List.of(args));
        }
    }

    record CountAll() implements SqlExpr {
    }

    record Arithmetic(SqlExpr left, String operator, SqlExpr right) implements SqlExpr {
    }

    record Cast(CastType type, SqlExpr value) implements SqlExpr {
    }

    record Extract(ExprFunction unit, SqlExpr value) implements SqlExpr {
    }

    record Truncate(String unit, SqlExpr value) implements SqlExpr {
    }

    record Length(SqlExpr value) implements SqlExpr {
    }

    record ExactText(SqlExpr value) implements SqlExpr {
    }

    record EnumName(SqlExpr ordinal, List<String> names) implements SqlExpr {

        public EnumName {
            names = List.copyOf(names);
        }
    }

    record Subquery(Select select) implements SqlExpr {
    }

    record RowNumber(List<SqlExpr> partition, List<Select.Order> order) implements SqlExpr {

        public RowNumber {
            partition = List.copyOf(partition);
            order = List.copyOf(order);
        }
    }
}
