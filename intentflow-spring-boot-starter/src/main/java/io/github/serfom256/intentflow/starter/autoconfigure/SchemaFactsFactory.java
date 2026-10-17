package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.schema.JdbcSchemaIntrospector;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
final class SchemaFactsFactory {

    private SchemaFactsFactory() {
    }

    static SchemaFacts create(ServiceCatalog catalog, List<NameResolver> resolvers, @Nullable DataSource dataSource,
            boolean introspect) {
        List<ServiceDescriptor> services = catalog.names().stream().map(catalog::get).toList();
        Set<String> tables = services.stream().map(s -> s.table().value()).collect(Collectors.toSet());
        Map<String, TableFacts> facts = new LinkedHashMap<>(introspected(dataSource, introspect, tables));
        Map<ServiceName, String> restrictions = new LinkedHashMap<>();
        for (ServiceDescriptor service : services) {
            TableFacts declared = ServiceDescriptors.factsOf(service.resultType(), resolvers);
            facts.merge(service.table().value(), declared, TableFacts::merge);
            ServiceDescriptors.restrictionOf(service.resultType(), resolvers)
                    .ifPresent(sql -> restrictions.put(service.name(), sql));
        }
        if (!restrictions.isEmpty()) {
            log.info("IntentFlow applies row restrictions to {}", restrictions.keySet().stream()
                    .map(ServiceName::value).sorted().toList());
        }
        return SchemaFacts.of(facts, restrictions);
    }

    private static Map<String, TableFacts> introspected(@Nullable DataSource dataSource, boolean introspect,
            Set<String> tables) {
        if (!introspect) {
            log.info("IntentFlow schema introspection is disabled; SQL relies on ORM and annotation facts only");
            return Map.of();
        }
        if (dataSource == null) {
            return Map.of();
        }
        try {
            Map<String, TableFacts> facts = JdbcSchemaIntrospector.introspect(dataSource, tables);
            List<String> missing = tables.stream().filter(t -> !facts.containsKey(t)).sorted().toList();
            log.info("IntentFlow read the schema of {} of {} tables{}", facts.size(), tables.size(),
                    missing.isEmpty() ? "" : "; not found: " + missing);
            return facts;
        } catch (Exception e) {
            log.warn("IntentFlow could not read the database schema, falling back to ORM and annotation facts: {}",
                    e.getMessage());
            return Map.of();
        }
    }
}
