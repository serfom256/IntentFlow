package io.github.serfom256.intentflow.springdata.cassandra;

import io.github.serfom256.intentflow.cassandra.CassandraQueryBuilder;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.testing.DifferentialTestBase;
import io.github.serfom256.intentflow.core.testing.GadgetKind;
import org.junit.jupiter.api.AfterAll;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

class CassandraDifferentialIT extends DifferentialTestBase {

    @Table("gadget")
    @IntentService(value = "gadgets", uri = "/gadgets/{{id}}", maxTake = 500, defaultSort = "id")
    public static class Gadget {
        @PrimaryKey @IntentField Long id;
        @IntentField String name;
        @IntentField(caseSensitive = true) GadgetKind kind;
        @IntentField BigDecimal price;
        @IntentField Integer stock;
        @IntentField Boolean active;
        @Column("created_at") @IntentField Instant createdAt;
    }

    private CqlSession session;

    @Override
    protected Class<?> entityType() {
        return Gadget.class;
    }

    @Override
    protected List<NameResolver> nameResolvers() {
        return List.of(new CassandraNameResolver());
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return new CassandraQueryBuilder(session);
    }

    @Override
    protected String target() {
        return "CASSANDRA";
    }

    @Override
    protected QueryExecutor prepare(List<Map<String, Object>> rows) {
        session = CassandraTestDatabase.session();
        session.execute("DROP TABLE IF EXISTS gadget");
        session.execute("""
                CREATE TABLE gadget (
                    id bigint PRIMARY KEY,
                    name text,
                    kind text,
                    price decimal,
                    stock int,
                    active boolean,
                    created_at timestamp)
                """);
        PreparedStatement insert = session.prepare(
                "INSERT INTO gadget (id, name, kind, price, stock, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)");
        for (Map<String, Object> row : rows) {
            BigDecimal stock = (BigDecimal) row.get("stock");
            LocalDateTime createdAt = (LocalDateTime) row.get("created_at");
            session.execute(insert.bind(
                    ((BigDecimal) row.get("id")).longValueExact(),
                    row.get("name"),
                    row.get("kind"),
                    row.get("price"),
                    stock == null ? null : stock.intValueExact(),
                    row.get("active"),
                    createdAt == null ? null : createdAt.toInstant(ZoneOffset.UTC)));
        }
        return CassandraQueryExecutor.of(session);
    }

    @AfterAll
    void close() {
        if (session != null) {
            session.close();
        }
    }
}
