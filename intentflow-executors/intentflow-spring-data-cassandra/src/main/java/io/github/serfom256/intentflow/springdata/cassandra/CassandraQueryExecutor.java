package io.github.serfom256.intentflow.springdata.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.CqlIdentifier;
import io.github.serfom256.intentflow.cassandra.CassandraAggregate;
import io.github.serfom256.intentflow.cassandra.CassandraQuery;
import io.github.serfom256.intentflow.cassandra.CqlValues;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.pipeline.ScanLimitExceededException;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.mapping.SimpleUserTypeResolver;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

public final class CassandraQueryExecutor implements QueryExecutor {

    public static final int DEFAULT_MAX_SCANNED_ROWS = 10_000;

    private final CqlSession session;
    private final CassandraConverter converter;
    private final int maxScannedRows;

    public CassandraQueryExecutor(CqlSession session, CassandraConverter converter, int maxScannedRows) {
        if (maxScannedRows < 1) {
            throw new IllegalArgumentException("maxScannedRows must be positive: " + maxScannedRows);
        }
        this.session = session;
        this.converter = converter;
        this.maxScannedRows = maxScannedRows;
    }

    public static CassandraQueryExecutor of(CqlSession session) {
        MappingCassandraConverter converter = new MappingCassandraConverter();
        converter.setUserTypeResolver(new SimpleUserTypeResolver(session));
        converter.setCodecRegistry(session.getContext().getCodecRegistry());
        converter.afterPropertiesSet();
        return new CassandraQueryExecutor(session, converter, DEFAULT_MAX_SCANNED_ROWS);
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        CassandraQuery q = cassandraQuery(query);
        List<Row> matches = matching(q, q.order() == null ? q.skip() + q.limit() : Integer.MAX_VALUE);
        if (q.order() != null) {
            matches.sort(q.order());
        }
        return matches.stream().skip(q.skip()).limit(q.limit()).map(row -> converter.read(resultType, row)).toList();
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        CassandraQuery q = cassandraQuery(query);
        CassandraAggregate aggregate = q.aggregate();
        if (aggregate == null) {
            throw new IllegalArgumentException("not an aggregate query: " + q.cql());
        }
        Map<List<@Nullable Object>, Accumulator> groups = new LinkedHashMap<>();
        for (Row row : matching(q, Integer.MAX_VALUE)) {
            List<@Nullable Object> key = new ArrayList<>(aggregate.groupColumns().size());
            for (CqlIdentifier column : aggregate.groupColumns()) {
                key.add(CqlValues.comparable(row.getObject(column)));
            }
            Object value = aggregate.column() == null ? null : CqlValues.comparable(row.getObject(aggregate.column()));
            groups.computeIfAbsent(key, k -> new Accumulator(aggregate.function())).add(value);
        }
        if (groups.isEmpty() && aggregate.groupColumns().isEmpty()) {
            groups.put(List.of(), new Accumulator(aggregate.function()));
        }
        List<List<@Nullable Object>> rows = new ArrayList<>(groups.size());
        groups.forEach((key, accumulator) -> {
            List<@Nullable Object> row = new ArrayList<>(key);
            row.add(accumulator.result());
            rows.add(row);
        });
        rows.sort(aggregate.order());
        return rows.stream().skip(q.skip()).limit(q.limit()).toList();
    }

    private static CassandraQuery cassandraQuery(BoundQuery query) {
        if (!(query instanceof CassandraQuery q)) {
            throw new IllegalArgumentException("CassandraQueryExecutor runs queries built by CassandraQueryBuilder, got "
                    + query.getClass().getName());
        }
        return q;
    }

    private List<Row> matching(CassandraQuery q, int enough) {
        String cql = q.cql();
        List<Row> matches = new ArrayList<>();
        int scanned = 0;
        for (Row row : session.execute(SimpleStatement.newInstance(cql, q.bindValues().toArray()))) {
            if (++scanned > maxScannedRows) {
                throw new ScanLimitExceededException(cql, maxScannedRows);
            }
            if (q.filter().test(row)) {
                matches.add(row);
                if (matches.size() >= enough) {
                    break;
                }
            }
        }
        return matches;
    }

    private static final class Accumulator {

        private final AggregateFunction function;
        private long count;
        private long present;
        private @Nullable BigDecimal sum;
        private @Nullable Comparable<Object> extreme;

        Accumulator(AggregateFunction function) {
            this.function = function;
        }

        @SuppressWarnings("unchecked")
        void add(@Nullable Object value) {
            count++;
            if (value == null) {
                return;
            }
            present++;
            switch (function) {
                case SUM, AVG -> sum = sum == null ? (BigDecimal) value : sum.add((BigDecimal) value);
                case MIN -> extreme = extreme == null || extreme.compareTo(value) > 0 ? (Comparable<Object>) value : extreme;
                case MAX -> extreme = extreme == null || extreme.compareTo(value) < 0 ? (Comparable<Object>) value : extreme;
                case COUNT -> { }
            }
        }

        @Nullable Object result() {
            return switch (function) {
                case COUNT -> BigDecimal.valueOf(count);
                case SUM -> sum;
                case AVG -> sum == null ? null : sum.divide(BigDecimal.valueOf(present), MathContext.DECIMAL64);
                case MIN, MAX -> extreme;
            };
        }
    }
}
