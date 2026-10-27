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
import java.util.Optional;

public record DefaultAstQuery(ServiceName service, Optional<FilterExpr> filter, List<SortKey> sort, Optional<Skip> skip,
        Optional<Take> take, Optional<Aggregate> aggregate, Optional<TopN> top) implements AstQuery {

    public DefaultAstQuery {
        sort = List.copyOf(sort);
    }

    public DefaultAstQuery(ServiceName service, @Nullable FilterExpr filter, List<SortKey> sort, @Nullable Skip skip,
            @Nullable Take take) {
        this(service, filter, sort, skip, take, null);
    }

    public DefaultAstQuery(ServiceName service, @Nullable FilterExpr filter, List<SortKey> sort, @Nullable Skip skip,
            @Nullable Take take, @Nullable Aggregate aggregate) {
        this(service, Optional.ofNullable(filter), sort, Optional.ofNullable(skip), Optional.ofNullable(take),
                Optional.ofNullable(aggregate), Optional.empty());
    }

    public static DefaultAstQuery of(ServiceName service, @Nullable FilterExpr filter, List<SortKey> sort,
            @Nullable Skip skip, @Nullable Take take, @Nullable Aggregate aggregate, @Nullable TopN top) {
        return new DefaultAstQuery(service, Optional.ofNullable(filter), sort, Optional.ofNullable(skip),
                Optional.ofNullable(take), Optional.ofNullable(aggregate), Optional.ofNullable(top));
    }
}
