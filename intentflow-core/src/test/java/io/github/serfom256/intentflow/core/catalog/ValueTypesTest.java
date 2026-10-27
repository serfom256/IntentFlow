package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptions;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTypesTest {

    @Test
    void serviceNameRejectsBlank() {
        assertThatThrownBy(() -> ServiceNames.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void serviceDescriptionRejectsBlank() {
        assertThatThrownBy(() -> ServiceDescriptions.of("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("omit");
    }

    @Test
    void tableNameRejectsBlank() {
        assertThatThrownBy(() -> TableNames.of(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void logicalFieldNameRejectsBlank() {
        assertThatThrownBy(() -> LogicalFieldNames.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void columnNameRejectsBlank() {
        assertThatThrownBy(() -> ColumnNames.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void uriTemplateRejectsBlank() {
        assertThatThrownBy(() -> UriTemplates.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maxTakeMustBePositive() {
        assertThatThrownBy(() -> MaxTakes.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MaxTakes.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(MaxTakes.of(1).value()).isEqualTo(1);
    }
}
