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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceDescriptorTest {

    private static final ServiceName NAME = ServiceNames.of("books");
    private static final TableName TABLE = TableNames.of("book");
    private static final MaxTake MAX_TAKE = MaxTakes.of(100);
    private static final UriTemplate URI = UriTemplates.of("/books?ids={{ids}}");

    private static final FieldMapping ID = FieldMappings.of(
            LogicalFieldNames.of("id"), ColumnNames.of("id"), true);
    private static final FieldMapping TITLE = FieldMappings.of(
            LogicalFieldNames.of("title"), ColumnNames.of("title"), true);
    private static final FieldMapping AUTHOR = FieldMappings.of(
            LogicalFieldNames.of("author"), ColumnNames.of("author_name"), true);

    @Test
    void buildsWithMinimalValidFields() {
        ServiceDescriptor svc = ServiceDescriptors.of(
                NAME, null, TABLE, List.of(ID, TITLE, AUTHOR),
                List.of(), MAX_TAKE, URI, Object.class);
        assertThat(svc.name()).isEqualTo(NAME);
        assertThat(svc.description()).isNull();
        assertThat(svc.resultType()).isEqualTo(Object.class);
        assertThat(svc.defaultSort()).isEmpty();
        assertThat(svc.fields()).hasSize(3);
    }

    @Test
    void rejectsEmptyFields() {
        assertThatThrownBy(() -> ServiceDescriptors.of(
                NAME, null, TABLE, List.of(), List.of(), MAX_TAKE, URI, Object.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void rejectsDuplicateLogicalField() {
        FieldMapping dupId = FieldMappings.of(
                LogicalFieldNames.of("id"), ColumnNames.of("other_id"), true);
        assertThatThrownBy(() -> ServiceDescriptors.of(
                NAME, null, TABLE, List.of(ID, dupId), List.of(), MAX_TAKE, URI, Object.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsDefaultSortReferencingUndeclaredField() {
        SortKey badSort = SortKeys.of(LogicalFieldNames.of("ghost"), SortKey.Direction.ASC);
        assertThatThrownBy(() -> ServiceDescriptors.of(
                NAME, null, TABLE, List.of(ID), List.of(badSort), MAX_TAKE, URI, Object.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not declared");
    }

    @Test
    void acceptsDefaultSortReferencingDeclaredField() {
        SortKey sort = SortKeys.of(LogicalFieldNames.of("title"), SortKey.Direction.ASC);
        ServiceDescriptor svc = ServiceDescriptors.of(
                NAME, ServiceDescriptions.of("library"), TABLE,
                List.of(ID, TITLE), List.of(sort),
                MAX_TAKE, URI, String.class);
        assertThat(svc.defaultSort()).containsExactly(sort);
        assertThat(svc.description().value()).isEqualTo("library");
        assertThat(svc.resultType()).isEqualTo(String.class);
    }
}
