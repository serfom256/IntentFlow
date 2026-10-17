package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotatedCatalogTest {

    enum Category {
        LAPTOP, PHONE, SMART_WATCH
    }

    @IntentService(value = "products", uri = "/products/{{id}}", maxTake = 25, description = "Things we sell", defaultSort = {
            "rating desc", "id" })
    record ProductRecord(
            @IntentField(queryable = false) long id,
            @IntentField String name,
            @IntentField Category category,
            @IntentField(column = "price_usd", description = "USD") BigDecimal price,
            @IntentField double rating,
            @IntentField boolean inStock,
            @IntentField Instant createdAt,
            @IntentField LocalDate releaseDay,
            @IntentField UUID externalRef,
            @IntentField(name = "tier", values = { "gold", "silver" }) String loyaltyTier,
            String internalNotes) {
    }

    static class BaseEntity {
        @IntentField
        Long id;
        @IntentField
        Instant createdAt;
        static final String IGNORED_CONSTANT = "x";
    }

    @IntentService(value = "customers", table = "app_customer", uri = "/customers/{{id}}")
    static class Customer extends BaseEntity {
        @IntentField(name = "name", column = "full_name")
        String fullName;
        @IntentField
        Integer ordersCount;
        Object notExposed;
    }

    private static Map<String, FieldMapping> byName(ServiceDescriptor d) {
        return d.fields().stream().collect(Collectors.toMap(f -> f.logical().value(), Function.identity()));
    }

    @Nested
    class Records {

        private final ServiceDescriptor d = ServiceDescriptors.fromAnnotated(ProductRecord.class);

        @Test
        void serviceLevelAttributes() {
            assertThat(d.name().value()).isEqualTo("products");
            assertThat(d.table().value()).isEqualTo("product_record");
            assertThat(d.description()).isNotNull();
            assertThat(d.description().value()).isEqualTo("Things we sell");
            assertThat(d.uriTemplate().value()).isEqualTo("/products/{{id}}");
            assertThat(d.maxTake().value()).isEqualTo(25);
            assertThat(d.resultType()).isEqualTo(ProductRecord.class);
        }

        @Test
        void fieldsInComponentOrderAndUnannotatedSkipped() {
            assertThat(d.fields()).extracting(f -> f.logical().value()).containsExactly(
                    "id", "name", "category", "price", "rating", "in_stock", "created_at",
                    "release_day", "external_ref", "tier");
        }

        @Test
        void typesInferredFromJavaTypes() {
            Map<String, FieldMapping> f = byName(d);
            assertThat(f.get("id").type()).isEqualTo(FieldType.NUMBER);
            assertThat(f.get("name").type()).isEqualTo(FieldType.TEXT);
            assertThat(f.get("category").type()).isEqualTo(FieldType.TEXT);
            assertThat(f.get("price").type()).isEqualTo(FieldType.NUMBER);
            assertThat(f.get("rating").type()).isEqualTo(FieldType.NUMBER);
            assertThat(f.get("in_stock").type()).isEqualTo(FieldType.BOOLEAN);
            assertThat(f.get("created_at").type()).isEqualTo(FieldType.TIMESTAMP);
            assertThat(f.get("release_day").type()).isEqualTo(FieldType.TIMESTAMP);
            assertThat(f.get("external_ref").type()).isEqualTo(FieldType.TEXT);
        }

        @Test
        void columnsDefaultToSnakeCaseAndCanBeOverridden() {
            Map<String, FieldMapping> f = byName(d);
            assertThat(f.get("in_stock").column().value()).isEqualTo("in_stock");
            assertThat(f.get("price").column().value()).isEqualTo("price_usd");
            assertThat(f.get("tier").column().value()).isEqualTo("loyalty_tier");
        }

        @Test
        void enumConstantsBecomeAllowedValuesAndExplicitValuesWin() {
            Map<String, FieldMapping> f = byName(d);
            assertThat(f.get("category").allowedValues()).containsExactly("LAPTOP", "PHONE", "SMART_WATCH");
            assertThat(f.get("tier").allowedValues()).containsExactly("gold", "silver");
            assertThat(f.get("name").allowedValues()).isEmpty();
        }

        @Test
        void descriptionAndQueryableFlags() {
            Map<String, FieldMapping> f = byName(d);
            assertThat(f.get("price").description()).isEqualTo("USD");
            assertThat(f.get("name").description()).isNull();
            assertThat(f.get("id").queryable()).isFalse();
            assertThat(f.get("name").queryable()).isTrue();
        }

        @Test
        void defaultSortParsed() {
            assertThat(d.defaultSort()).extracting(k -> k.field().value(), SortKey::direction)
                    .containsExactly(
                            Tuple.tuple("rating", SortKey.Direction.DESC),
                            Tuple.tuple("id", SortKey.Direction.ASC));
        }
    }

    @Nested
    class Classes {

        private final ServiceDescriptor d = ServiceDescriptors.fromAnnotated(Customer.class);

        @Test
        void superclassFieldsComeFirstStaticsSkipped() {
            assertThat(d.fields()).extracting(f -> f.logical().value())
                    .containsExactly("id", "created_at", "name", "orders_count");
        }

        @Test
        void explicitTableAndRenamedField() {
            assertThat(d.table().value()).isEqualTo("app_customer");
            assertThat(byName(d).get("name").column().value()).isEqualTo("full_name");
            assertThat(d.description()).isNull();
        }
    }

    @Nested
    class Errors {

        static class NotAService {
            @IntentField
            String x;
        }

        @IntentService(value = "empty", uri = "/e")
        static class NoFields {
            String x;
        }

        @IntentService(value = "weird", uri = "/w")
        static class UnsupportedType {
            @IntentField
            List<String> tags;
        }

        @IntentService(value = "weird", uri = "/w")
        static class ExplicitTypeRescuesUnsupported {
            @IntentField(type = FieldType.TEXT)
            List<String> tags;
        }

        @IntentService(value = "s", uri = "/s", defaultSort = "nope")
        static class SortOnUnknownField {
            @IntentField
            String x;
        }

        @IntentService(value = "s", uri = "/s", defaultSort = "x sideways")
        static class BadSortDirection {
            @IntentField
            String x;
        }

        @IntentService(value = "s", uri = "/s")
        static class DuplicateLogicalNames {
            @IntentField(name = "x")
            String a;
            @IntentField(name = "x")
            String b;
        }

        @IntentService(value = "s", uri = "/s")
        static class ValuesOnNumber {
            @IntentField(values = { "1", "2" })
            int x;
        }

        @Test
        void missingServiceAnnotation() {
            assertThatThrownBy(() -> ServiceDescriptors.fromAnnotated(NotAService.class))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("@IntentService");
        }

        @Test
        void noExposedFields() {
            assertThatThrownBy(() -> ServiceDescriptors.fromAnnotated(NoFields.class))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("@IntentField");
        }

        @Test
        void typeCannotBeInferred() {
            assertThatThrownBy(() -> ServiceDescriptors.fromAnnotated(UnsupportedType.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("UnsupportedType.tags")
                    .hasMessageContaining("type = ...");
            assertThat(ServiceDescriptors.fromAnnotated(ExplicitTypeRescuesUnsupported.class)
                    .fields().getFirst().type()).isEqualTo(FieldType.TEXT);
        }

        @Test
        void invalidDefaultSort() {
            assertThatThrownBy(() -> ServiceDescriptors.fromAnnotated(SortOnUnknownField.class))
                    .hasMessageContaining("'nope' is not declared");
            assertThatThrownBy(() -> ServiceDescriptors.fromAnnotated(BadSortDirection.class))
                    .hasMessageContaining("sideways");
        }

        @Test
        void duplicateLogicalNames() {
            assertThatThrownBy(() -> ServiceDescriptors.fromAnnotated(DuplicateLogicalNames.class))
                    .hasMessageContaining("duplicate logical field: x");
        }

        @Test
        void allowedValuesOnlyForText() {
            assertThatThrownBy(() -> ServiceDescriptors.fromAnnotated(ValuesOnNumber.class))
                    .hasMessageContaining("only apply to text fields");
        }
    }

    @Nested
    class Catalogs {

        @Test
        void annotatedAndProgrammaticDescriptorsMix() {
            ServiceDescriptor manual = ServiceDescriptors.of(
                    ServiceNames.of("orders"), null, TableNames.of("orders"),
                    List.of(FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("id"), true)),
                    List.of(), MaxTakes.of(10), UriTemplates.of("/orders/{{id}}"), Object.class);

            ServiceCatalog catalog = ServiceCatalogs.of(
                    ServiceDescriptors.fromAnnotated(ProductRecord.class),
                    ServiceDescriptors.fromAnnotated(Customer.class),
                    manual);

            assertThat(catalog.names()).extracting(ServiceName::value)
                    .containsExactlyInAnyOrder("products", "customers", "orders");
            assertThat(catalog.get(ServiceNames.of("orders"))).isSameAs(manual);
            assertThat(catalog.find(ServiceNames.of("nope"))).isEmpty();
            assertThat(manual.fields().getFirst().type()).isEqualTo(FieldType.ANY);
        }

        @Test
        void fromAnnotatedShortcut() {
            assertThat(ServiceCatalogs.fromAnnotated(ProductRecord.class, Customer.class).names()).hasSize(2);
        }

        @Test
        void duplicateServiceNamesRejected() {
            assertThatThrownBy(() -> ServiceCatalogs.fromAnnotated(ProductRecord.class, ProductRecord.class))
                    .hasMessageContaining("duplicate service: products");
        }
    }
}
