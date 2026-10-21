package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcCatalogOverridesTest {

    private static final ServiceName BOOKS = ServiceNames.of("books");

    private final DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");

    private JdbcCatalogOverrides store(Duration refresh) {
        return new JdbcCatalogOverrides(dataSource, "intentflow_catalog_override", true, refresh);
    }

    @Test
    void storesReplacesAndRemovesOverrides() {
        JdbcCatalogOverrides store = store(Duration.ofMinutes(1));
        long initial = store.version();

        store.put(BOOKS, new ServiceOverride("Every book", "/catalog/{{isbn}}", Map.of("title", "full title")));
        assertThat(store.find(BOOKS)).contains(
                new ServiceOverride("Every book", "/catalog/{{isbn}}", Map.of("title", "full title")));
        assertThat(store.version()).isGreaterThan(initial);

        store.put(BOOKS, new ServiceOverride(null, null, Map.of("isbn", "ISBN-13 with dashes")));
        assertThat(store.find(BOOKS)).contains(new ServiceOverride(null, null, Map.of("isbn", "ISBN-13 with dashes")));

        store.remove(BOOKS);
        assertThat(store.find(BOOKS)).isEmpty();
        assertThat(store.all()).isEmpty();
    }

    @Test
    void anotherInstanceSeesChangesAfterItsRefreshInterval() {
        JdbcCatalogOverrides writer = store(Duration.ofMinutes(1));
        JdbcCatalogOverrides reader = store(Duration.ZERO);
        long before = reader.version();

        writer.put(BOOKS, new ServiceOverride("Every book", null, Map.of()));

        assertThat(reader.find(BOOKS)).map(ServiceOverride::description).contains("Every book");
        assertThat(reader.version()).isGreaterThan(before);
        assertThat(store(Duration.ofMinutes(1)).find(BOOKS)).isPresent();
    }

    @Test
    void rejectsUnsafeTableNames() {
        assertThatThrownBy(() -> new JdbcCatalogOverrides(dataSource, "x; DROP TABLE y", true, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMissingTableExplainsHowToCreateIt() {
        assertThatThrownBy(() -> new JdbcCatalogOverrides(dataSource, "intentflow_catalog_override", false,
                Duration.ZERO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("intentflow_catalog_override")
                .hasMessageContaining("classpath:db/intentflow/intentflow-admin.changelog.yaml")
                .hasMessageContaining("intentflow.admin.create-table=true");
    }
}
