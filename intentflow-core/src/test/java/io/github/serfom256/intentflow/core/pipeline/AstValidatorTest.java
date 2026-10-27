package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AstValidatorTest {

    private final AstValidator validator = AstValidators.create();

    private static final ServiceName BOOKS = ServiceNames.of("books");
    private static final LogicalFieldName ID = LogicalFieldNames.of("id");
    private static final LogicalFieldName TITLE = LogicalFieldNames.of("title");
    private static final LogicalFieldName AUTHOR = LogicalFieldNames.of("author");
    private static final LogicalFieldName SECRET = LogicalFieldNames.of("secret");
    private static final LogicalFieldName GHOST = LogicalFieldNames.of("ghost");

    private static ServiceCatalog catalogWithBooks(int maxTake) {
        ServiceDescriptor svc = ServiceDescriptors.of(BOOKS, null, TableNames.of("book"),
                List.of(FieldMappings.of(ID, ColumnNames.of("id"), true),
                        FieldMappings.of(TITLE, ColumnNames.of("title"), true),
                        FieldMappings.of(AUTHOR, ColumnNames.of("author_name"), true),
                        FieldMappings.of(SECRET, ColumnNames.of("secret_col"), false)),
                List.of(), MaxTakes.of(maxTake), UriTemplates.of("/books?ids={{ids}}"), Object.class);
        Map<ServiceName, ServiceDescriptor> entries = Map.of(BOOKS, svc);
        return new ServiceCatalog() {
            @Override
            public Optional<ServiceDescriptor> find(ServiceName name) {
                return Optional.ofNullable(entries.get(name));
            }

            @Override
            public Set<ServiceName> names() {
                return entries.keySet();
            }

            @Override
            public int version() {
                return 1;
            }
        };
    }

    private static AstQuery query(FilterExpr filter, List<SortKey> sort, Take take) {
        return AstQueries.of(BOOKS, filter, sort, null, take);
    }

    @Test
    void serviceOnlyQueryPasses() {
        ValidatedQuery v = validator.validate(AstQueries.of(BOOKS, null, List.of(), null, null), catalogWithBooks(100));
        assertThat(v.service().name()).isEqualTo(BOOKS);
        assertThat(v.ast().filter()).isEmpty();
    }

    @Test
    void filterWithQueryableFieldsPasses() {
        FilterExpr filter = new And(new Compare(AUTHOR, ComparisonOp.EQ, new TextLiteral("Doe")),
                new Compare(TITLE, ComparisonOp.CONTAINS, new TextLiteral("ring")));
        assertThat(validator.validate(query(filter, List.of(), null), catalogWithBooks(100))).isNotNull();
    }

    @Test
    void inListAndNullChecksTraverseCorrectly() {
        FilterExpr filter = new And(new In(TITLE, List.of(new TextLiteral("a"), new TextLiteral("b"))),
                new Not(new IsNull(AUTHOR)));
        assertThat(validator.validate(query(filter, List.of(), null), catalogWithBooks(100))).isNotNull();
    }

    @Test
    void takeEqualToMaxTakePasses() {
        assertThat(validator.validate(query(null, List.of(), Takes.of(100)), catalogWithBooks(100))).isNotNull();
    }

    @Test
    void unknownServiceThrowsServiceNotFound() {
        AstQuery q = AstQueries.of(ServiceNames.of("nope"), null, List.of(), null, null);
        assertThatThrownBy(() -> validator.validate(q, catalogWithBooks(100)))
                .isInstanceOf(ServiceNotFoundException.class);
    }

    @Test
    void filterReferencingUndeclaredFieldFails() {
        FilterExpr filter = new Compare(GHOST, ComparisonOp.EQ, new TextLiteral("x"));
        assertThatThrownBy(() -> validator.validate(query(filter, List.of(), null), catalogWithBooks(100)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("ghost");
    }

    @Test
    void filterReferencingNonQueryableFieldFails() {
        FilterExpr filter = new Compare(SECRET, ComparisonOp.EQ, new TextLiteral("x"));
        assertThatThrownBy(() -> validator.validate(query(filter, List.of(), null), catalogWithBooks(100)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("secret");
    }

    @Test
    void sortFieldUndeclaredFails() {
        SortKey badSort = SortKeys.of(GHOST, SortKey.Direction.ASC);
        assertThatThrownBy(() -> validator.validate(query(null, List.of(badSort), null), catalogWithBooks(100)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("sort");
    }

    @Test
    void sortFieldNonQueryableFails() {
        SortKey badSort = SortKeys.of(SECRET, SortKey.Direction.ASC);
        assertThatThrownBy(() -> validator.validate(query(null, List.of(badSort), null), catalogWithBooks(100)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void takeExceedingMaxTakeFails() {
        assertThatThrownBy(() -> validator.validate(query(null, List.of(), Takes.of(101)), catalogWithBooks(100)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("maxTake");
    }

    @Test
    void deepFilterTreeChecksAllLeaves() {
        FilterExpr filter = new And(
                new Or(new Compare(AUTHOR, ComparisonOp.EQ, new TextLiteral("X")),
                        new Compare(TITLE, ComparisonOp.EQ, new TextLiteral("Y"))),
                new Not(new Compare(GHOST, ComparisonOp.EQ, new NumberLiteral(BigDecimal.ONE))));
        assertThatThrownBy(() -> validator.validate(query(filter, List.of(), null), catalogWithBooks(100)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("ghost");
    }

    @Test
    void skipValueIsAllowedAtZero() {
        ValidatedQuery v = validator.validate(AstQueries.of(BOOKS, null, List.of(), Skips.of(0), null),
                catalogWithBooks(100));
        assertThat(v.ast().skip().orElseThrow().value()).isZero();
    }
}
