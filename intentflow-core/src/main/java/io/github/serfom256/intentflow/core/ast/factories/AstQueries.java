package io.github.serfom256.intentflow.core.ast.factories;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.Skip;
import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.impl.DefaultAstQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AstQueries {

    public static AstQuery of(
            ServiceName service,
            @Nullable FilterExpr filter,
            List<SortKey> sort,
            @Nullable Skip skip,
            @Nullable Take take) {
        return new DefaultAstQuery(service, filter, sort, skip, take);
    }

    public static AstQuery of(
            ServiceName service,
            @Nullable FilterExpr filter,
            List<SortKey> sort,
            @Nullable Skip skip,
            @Nullable Take take,
            @Nullable Aggregate aggregate) {
        return new DefaultAstQuery(service, filter, sort, skip, take, aggregate);
    }
}
