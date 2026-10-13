package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultIntentPipeline;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IntentPipelines {

    public static IntentPipeline create(TextNormalizer normalizer,
                                        DslCache cache,
                                        LlmClient llm,
                                        DslParser parser,
                                        AstValidator validator,
                                        SqlBuilder sqlBuilder,
                                        QueryExecutor executor,
                                        UriBuilder uriBuilder,
                                        ServiceCatalog catalog) {
        return new DefaultIntentPipeline(
                normalizer, cache, llm, parser, validator,
                sqlBuilder, executor, uriBuilder, catalog);
    }
}
