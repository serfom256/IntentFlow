package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.RelatedRows;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

final class TransactionalQueryExecutor implements QueryExecutor {

    private final QueryExecutor delegate;
    private final TransactionTemplate transactions;

    TransactionalQueryExecutor(QueryExecutor delegate, PlatformTransactionManager manager, Duration timeout,
            boolean readOnly) {
        this.delegate = delegate;
        this.transactions = new TransactionTemplate(manager);
        transactions.setReadOnly(readOnly);
        if (timeout.isPositive()) {
            transactions.setTimeout((int) Math.max(1, timeout.toSeconds()));
        }
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        return inTransaction(() -> delegate.execute(query, resultType));
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType, RelatedRows related) {
        return inTransaction(() -> delegate.execute(query, resultType, related));
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        return inTransaction(() -> delegate.rows(query));
    }

    @Override
    public ResultShape shape(Class<?> resultType) {
        return delegate.shape(resultType);
    }

    @Override
    public List<RuntimeRestriction> restrictions(Class<?> entityType) {
        return delegate.restrictions(entityType);
    }

    private <R> R inTransaction(Supplier<R> work) {
        return transactions.execute(status -> work.get());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(PlatformTransactionManager.class)
    static class Wiring {

        @Bean
        ExecutorWrapper intentFlowTransactionalExecutor(ObjectProvider<PlatformTransactionManager> managers,
                IntentFlowProperties properties) {
            return executor -> {
                PlatformTransactionManager manager = managers.getIfUnique();
                IntentFlowProperties.Sql sql = properties.sql();
                if (manager == null || (!sql.readOnly() && !sql.statementTimeout().isPositive())) {
                    return executor;
                }
                return new TransactionalQueryExecutor(executor, manager, sql.statementTimeout(), sql.readOnly());
            };
        }
    }
}
