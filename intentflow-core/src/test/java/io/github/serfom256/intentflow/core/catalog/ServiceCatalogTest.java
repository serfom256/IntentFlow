package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceCatalogTest {

    private static ServiceDescriptor sample(ServiceName name) {
        return ServiceDescriptors.of(name, null, TableNames.of("book"),
                List.of(FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("id"), true)), List.of(),
                MaxTakes.of(100), UriTemplates.of("/x"), Object.class);
    }

    @Test
    void defaultGetReturnsDescriptorWhenPresent() {
        ServiceName name = ServiceNames.of("books");
        ServiceDescriptor svc = sample(name);
        ServiceCatalog catalog = stubCatalog(Map.of(name, svc));
        assertThat(catalog.get(name)).isSameAs(svc);
    }

    @Test
    void defaultGetThrowsServiceNotFoundWhenAbsent() {
        ServiceCatalog catalog = stubCatalog(Map.of());
        ServiceName ghost = ServiceNames.of("ghost");
        assertThatThrownBy(() -> catalog.get(ghost)).isInstanceOf(ServiceNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void serviceNotFoundExposesName() {
        ServiceName name = ServiceNames.of("ghost");
        ServiceNotFoundException ex = new ServiceNotFoundException(name);
        assertThat(ex.serviceName()).isEqualTo(name);
    }

    private static ServiceCatalog stubCatalog(Map<ServiceName, ServiceDescriptor> entries) {
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
}
