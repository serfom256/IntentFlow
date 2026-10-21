package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.core.query.executors.QueryExecutor;
import io.github.serfom256.core.query.handlers.EntityResultSetHandler;
import io.github.serfom256.core.query.handlers.IterableResultSetHandler;
import io.github.serfom256.core.query.source.mapping.InputMapper;
import io.github.serfom256.parser.SourceMapper;
import io.github.serfom256.procedures.Procedures;
import io.github.serfom256.procedures.ProceduresBuilder;
import io.github.serfom256.procedures.ProceduresCustomizer;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.time.Duration;

final class OpenMapperProcedures {

    private OpenMapperProcedures() {
    }

    static Procedures withTimeout(Procedures shared, Duration timeout, BeanFactory factory) {
        SourceMapper sourceMapper = factory.getBeanProvider(SourceMapper.class).getIfUnique();
        InputMapper inputMapper = factory.getBeanProvider(InputMapper.class).getIfUnique();
        QueryExecutor executor = factory.getBeanProvider(QueryExecutor.class).getIfUnique();
        EntityResultSetHandler entities = factory.getBeanProvider(EntityResultSetHandler.class).getIfUnique();
        IterableResultSetHandler iterables = factory.getBeanProvider(IterableResultSetHandler.class).getIfUnique();
        if (!timeout.isPositive() || sourceMapper == null || inputMapper == null || executor == null
                || entities == null || iterables == null) {
            return shared;
        }
        ObjectProvider<DataSource> dataSource = factory.getBeanProvider(DataSource.class);
        ProceduresBuilder builder = new ProceduresBuilder(sourceMapper, inputMapper, executor, entities, iterables,
                dataSource::getObject);
        factory.getBeanProvider(ProceduresCustomizer.class).orderedStream()
                .forEach(customizer -> customizer.customize(builder));
        return builder.queryTimeoutSeconds((int) Math.max(1, timeout.toSeconds())).build();
    }
}
