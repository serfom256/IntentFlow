package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatedQueryTest {

    @Test
    void holdsServiceAndAst() {
        ServiceName name = ServiceNames.of("books");
        ServiceDescriptor svc = ServiceDescriptors.of(name, null, TableNames.of("book"),
                List.of(FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("id"), true)), List.of(),
                MaxTakes.of(100), UriTemplates.of("/x"), Object.class);
        AstQuery ast = AstQueries.of(name, null, List.of(), null, null);
        ValidatedQuery v = ValidatedQueries.of(svc, ast);
        assertThat(v.service()).isSameAs(svc);
        assertThat(v.ast()).isSameAs(ast);
    }
}
