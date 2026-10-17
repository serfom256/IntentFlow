package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.Skip;
import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.TopN;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record DefaultAstQuery(
        ServiceName service,
        @Nullable FilterExpr filter,
        List<SortKey> sort,
        @Nullable Skip skip,
        @Nullable Take take,
        @Nullable Aggregate aggregate,
        @Nullable TopN top) implements AstQuery {

    public DefaultAstQuery {
        sort = List.copyOf(sort);
    }

    public DefaultAstQuery(ServiceName service, @Nullable FilterExpr filter, List<SortKey> sort, @Nullable Skip skip,
            @Nullable Take take) {
        this(service, filter, sort, skip, take, null, null);
    }

    public DefaultAstQuery(ServiceName service, @Nullable FilterExpr filter, List<SortKey> sort, @Nullable Skip skip,
            @Nullable Take take, @Nullable Aggregate aggregate) {
        this(service, filter, sort, skip, take, aggregate, null);
    }
}
