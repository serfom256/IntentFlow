package io.github.serfom256.intentflow.core.sql.render;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;

import java.util.ArrayList;
import java.util.List;

final class SqlBuffer implements SqlWriter {

    private final List<String> segments = new ArrayList<>();
    private final List<DslLiteral> params = new ArrayList<>();
    private final StringBuilder current = new StringBuilder(128);

    @Override
    public SqlBuffer text(String text) {
        current.append(text);
        return this;
    }

    @Override
    public SqlBuffer param(DslLiteral value) {
        segments.add(current.toString());
        current.setLength(0);
        params.add(value);
        return this;
    }

    BoundQuery toBoundQuery(List<String> optimizations) {
        List<String> all = new ArrayList<>(segments);
        all.add(current.toString());
        return BoundQueries.of(all, params, optimizations);
    }
}
