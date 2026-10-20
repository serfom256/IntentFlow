package io.github.serfom256.intentflow.dialect.h2;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.TestDatabase;

enum H2Database implements TestDatabase {

    UPPER_CASE("jdbc:h2:mem:conformance_upper;DB_CLOSE_DELAY=-1"),
    LOWER_CASE("jdbc:h2:mem:conformance_lower;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");

    private final String url;

    H2Database(String url) {
        this.url = url;
    }

    @Override
    public Endpoint endpoint() {
        return new Endpoint(url, "sa", "");
    }

    @Override
    public SqlDialect dialect() {
        return new H2Dialect();
    }

    @Override
    public String createGadgetTable() {
        return """
                CREATE TABLE gadget (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(32),
                    kind VARCHAR(16),
                    price NUMERIC(10,2),
                    stock INTEGER,
                    active BOOLEAN,
                    created_at TIMESTAMP)
                """;
    }
}
