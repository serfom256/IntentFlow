package io.github.serfom256.intentflow.dialect.clickhouse;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.images.builder.Transferable;

import java.time.ZoneId;

enum ClickHouseDatabase implements TestDatabase {

    SERVER;

    private static final String PROFILE = """
            <clickhouse>
                <profiles>
                    <default>
                        <data_type_default_nullable>1</data_type_default_nullable>
                        <default_table_engine>MergeTree</default_table_engine>
                        <session_timezone>%s</session_timezone>
                    </default>
                </profiles>
            </clickhouse>
            """.formatted(ZoneId.systemDefault().getId());

    private Endpoint endpoint;

    @Override
    public synchronized Endpoint endpoint() {
        if (endpoint == null) {
            ClickHouseContainer container = new ClickHouseContainer("clickhouse/clickhouse-server:26.3.46.2")
                    .withCopyToContainer(Transferable.of(PROFILE), "/etc/clickhouse-server/users.d/intentflow.xml");
            container.start();
            endpoint = new Endpoint(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }
        return endpoint;
    }

    @Override
    public SqlDialect dialect() {
        return new ClickHouseDialect();
    }

    @Override
    public String createGadgetTable() {
        return """
                CREATE TABLE gadget (
                    id Int64 NOT NULL PRIMARY KEY,
                    name String,
                    kind String,
                    price Decimal(10, 2),
                    stock Int32,
                    active Bool,
                    created_at DateTime)
                """;
    }

    @Override
    public String ddl(String createTable) {
        return createTable.replace(" PRIMARY KEY", " NOT NULL PRIMARY KEY")
                .replace("TIMESTAMP WITH TIME ZONE", "DateTime64(6, 'UTC')");
    }
}
