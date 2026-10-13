package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptions;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SortKeyTest {

    @Test
    void buildsAscendingKey() {
        SortKey key = SortKeys.of(LogicalFieldNames.of("year"), SortKey.Direction.ASC);
        assertThat(key.field().value()).isEqualTo("year");
        assertThat(key.direction()).isEqualTo(SortKey.Direction.ASC);
    }

    @Test
    void buildsDescendingKey() {
        SortKey key = SortKeys.of(LogicalFieldNames.of("year"), SortKey.Direction.DESC);
        assertThat(key.direction()).isEqualTo(SortKey.Direction.DESC);
    }
}
