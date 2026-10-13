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

class FieldMappingTest {

    @Test
    void holdsLogicalColumnAndQueryableFlag() {
        FieldMapping fm = FieldMappings.of(
                LogicalFieldNames.of("author"),
                ColumnNames.of("author_name"),
                true);
        assertThat(fm.logical().value()).isEqualTo("author");
        assertThat(fm.column().value()).isEqualTo("author_name");
        assertThat(fm.queryable()).isTrue();
    }
}
