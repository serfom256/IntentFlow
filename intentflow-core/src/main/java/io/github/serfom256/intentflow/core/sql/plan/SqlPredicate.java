package io.github.serfom256.intentflow.core.sql.plan;

import java.util.List;

/** A condition in a {@link Select}'s {@code WHERE} clause. */
public sealed interface SqlPredicate {

    record And(List<SqlPredicate> parts) implements SqlPredicate {

        public And {
            parts = List.copyOf(parts);
        }
    }

    record Or(List<SqlPredicate> parts) implements SqlPredicate {

        public Or {
            parts = List.copyOf(parts);
        }
    }

    record Not(SqlPredicate inner) implements SqlPredicate {
    }

    record Compare(SqlExpr left, String operator, SqlExpr right) implements SqlPredicate {
    }

    record IsNull(SqlExpr value, boolean negated) implements SqlPredicate {
    }

    record In(SqlExpr value, List<SqlExpr> values) implements SqlPredicate {

        public In {
            values = List.copyOf(values);
        }
    }

    record Contains(SqlExpr.Column column, String text) implements SqlPredicate {
    }

    record Like(SqlExpr value, SqlExpr pattern, char escape) implements SqlPredicate {
    }

    record Raw(String sql) implements SqlPredicate {
    }

    record Bound(List<String> segments, List<Object> values) implements SqlPredicate {
    }

    record Exists(Select select) implements SqlPredicate {
    }
}
