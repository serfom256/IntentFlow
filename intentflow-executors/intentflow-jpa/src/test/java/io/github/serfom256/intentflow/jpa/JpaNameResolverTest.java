package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JpaNameResolverTest {

    @Entity
    @Table(name = "customer_account")
    @IntentService(value = "accounts", uri = "/accounts/{{id}}")
    static class Account {
        @Id
        @IntentField
        Long id;
        @Column(name = "display_name")
        @IntentField
        String name;
        @Column
        @IntentField
        String countryCode;
        @IntentField(column = "is_vip")
        @Column(name = "vip_flag")
        Boolean vip;
    }

    @Entity
    @IntentService(value = "notes", uri = "/notes/{{id}}")
    static class AuditNote {
        @Id
        @IntentField
        Long id;
    }

    enum Level {
        LOW, HIGH
    }

    static class NotAnEntity {
        long id;
        Level level;
    }

    @Test
    void classesThatAreNotJpaEntitiesGetNoFacts() {
        assertThat(new JpaNameResolver().facts(NotAnEntity.class)).isEqualTo(TableFacts.UNKNOWN);
    }

    @Test
    void tableAndColumnsFromJpaAnnotations() {
        ServiceDescriptor d = ServiceDescriptors.fromAnnotated(Account.class, List.of(new JpaNameResolver()));

        assertThat(d.table().value()).isEqualTo("customer_account");
        assertThat(d.fields()).extracting(FieldMapping::column).extracting(c -> c.value()).containsExactly("id",
                "display_name", "country_code", "is_vip");
    }

    @Test
    void withoutTableAnnotationSnakeCaseMatchesSpringBootNaming() {
        assertThat(ServiceDescriptors.fromAnnotated(AuditNote.class, List.of(new JpaNameResolver())).table().value())
                .isEqualTo("audit_note");
    }
}
