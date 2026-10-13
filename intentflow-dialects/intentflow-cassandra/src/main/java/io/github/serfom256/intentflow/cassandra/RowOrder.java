package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.Row;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RowOrder {

    static @Nullable Comparator<Row> of(List<SortKey> keys, Map<String, CqlIdentifier> columns) {
        Comparator<Row> order = null;
        for (SortKey key : keys) {
            CqlIdentifier column = columns.get(key.field().value());
            if (column == null) {
                throw new IllegalArgumentException("logical field '" + key.field().value() + "' has no column mapping");
            }
            Comparator<Comparable<?>> values = RowOrder::compareValues;
            if (key.direction() == SortKey.Direction.DESC) {
                values = values.reversed();
            }
            Comparator<Row> byKey = Comparator.comparing(
                    (Row row) -> CqlValues.comparable(row.getObject(column)), Comparator.nullsLast(values));
            order = order == null ? byKey : order.thenComparing(byKey);
        }
        return order;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Comparable a, Comparable b) {
        return a.compareTo(b);
    }

    static Comparator<List<@Nullable Object>> ofValues(List<SortKey> keys, List<String> columns) {
        Comparator<List<@Nullable Object>> order = (a, b) -> 0;
        for (SortKey key : keys) {
            int index = columns.indexOf(key.field().value());
            if (index < 0) {
                throw new IllegalArgumentException("cannot sort aggregate rows by '" + key.field().value() + "'");
            }
            Comparator<Comparable<?>> values = RowOrder::compareValues;
            if (key.direction() == SortKey.Direction.DESC) {
                values = values.reversed();
            }
            order = order.thenComparing(row -> (Comparable<?>) row.get(index), Comparator.nullsLast(values));
        }
        return order;
    }
}
