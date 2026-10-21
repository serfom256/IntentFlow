package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.AuditLog;
import io.github.serfom256.intentflow.core.pipeline.CatalogChangeAudit;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogAdminControllerTest {

    private final ServiceCatalog catalog = ServiceCatalogs.withOverrides(ServiceCatalogs.fromAnnotated(Book.class),
            ServiceCatalogs.inMemoryOverrides());
    private static final ServiceName BOOKS = ServiceNames.of("books");

    private final List<String> prompts = new CopyOnWriteArrayList<>();
    private final List<CatalogChangeAudit> changes = new CopyOnWriteArrayList<>();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new CatalogAdminController(new CatalogAdmin(catalog,
            IntentPipelines.builder()
                    .llm((text, scope) -> {
                        prompts.add(scope.get(BOOKS).fields().get(1).description());
                        return text.value().contains("decline")
                                ? DslSources.of("unsupported \"no such data\"")
                                : DslSources.of("books | filter title ~ \"dune\"");
                    })
                    .sqlBuilder(SqlBuilders.create(new PostgresDialect()))
                    .executor(new QueryExecutor() {
                        @Override
                        public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                            throw new AssertionError("explain must not run queries");
                        }
                    })
                    .catalog(catalog)
                    .build(),
            null, new AuditLog() {
                @Override
                public void catalogChange(CatalogChangeAudit audit) {
                    changes.add(audit);
                }
            })))
            .build();

    @Test
    void listsDeclaredAndOverriddenValues() throws Exception {
        mvc.perform(get("/intentflow-admin/api/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("books"))
                .andExpect(jsonPath("$[0].entity").value("Book"))
                .andExpect(jsonPath("$[0].declaredDescription").value("Books in stock"))
                .andExpect(jsonPath("$[0].declaredUri").value("/books/{{id}}"))
                .andExpect(jsonPath("$[0].overrideUri").value(nullValue()))
                .andExpect(jsonPath("$[0].fields[*].name", contains("id", "title", "isbn")))
                .andExpect(jsonPath("$[0].fields[1].declaredDescription").value("title as printed"));
    }

    @Test
    void savesAnOverrideThatTheLlmSeesAndResetRestoresTheDeclaredValues() throws Exception {
        mvc.perform(put("/intentflow-admin/api/services/books").contentType(MediaType.APPLICATION_JSON)
                .requestAttr(AdminAccessInterceptor.USER, "lea")
                .content("""
                        {"description": "Every book we sell", "uri": "/catalog/{{isbn}}",
                         "fields": {"title": "full title including subtitle", "isbn": " "}}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrideDescription").value("Every book we sell"))
                .andExpect(jsonPath("$.overrideUri").value("/catalog/{{isbn}}"))
                .andExpect(jsonPath("$.fields[1].overrideDescription").value("full title including subtitle"))
                .andExpect(jsonPath("$.fields[2].overrideDescription").value(nullValue()));

        mvc.perform(post("/intentflow-admin/api/explain").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"dune\", \"service\": \"books\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dsl").value("books | filter title ~ \"dune\""))
                .andExpect(jsonPath("$.sql").value(startsWith("SELECT * FROM \"book\" WHERE")));

        mvc.perform(delete("/intentflow-admin/api/services/books").requestAttr(AdminAccessInterceptor.USER, "max"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overrideDescription").value(nullValue()))
                .andExpect(jsonPath("$.fields[1].overrideDescription").value(nullValue()));

        assertThat(prompts).containsExactly("full title including subtitle");
        assertThat(changes).extracting(CatalogChangeAudit::user, CatalogChangeAudit::action,
                change -> change.before().description(), change -> change.after().description())
                .containsExactly(
                        tuple("lea", CatalogChangeAudit.Action.UPDATED, null, "Every book we sell"),
                        tuple("max", CatalogChangeAudit.Action.RESET, "Every book we sell", null));
    }

    @Test
    void rejectsInvalidOverridesWithEveryReason() throws Exception {
        mvc.perform(put("/intentflow-admin/api/services/books").contentType(MediaType.APPLICATION_JSON)
                .content("{\"uri\": \"books/{{author}}\", \"fields\": {\"author\": \"who\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.kind").value("invalid"))
                .andExpect(jsonPath("$.errors", containsInAnyOrder(
                        "'author' is not a queryable field of books",
                        "uri must start with '/' or be an absolute URL",
                        "uri placeholder {{author}} is not a field of books")));
        mvc.perform(get("/intentflow-admin/api/services/books"))
                .andExpect(jsonPath("$.overrideUri").value(nullValue()));
    }

    @Test
    void unknownServicesAndDeclinedQuestionsAreReported() throws Exception {
        mvc.perform(get("/intentflow-admin/api/services/authors"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.kind").value("not-found"));
        mvc.perform(post("/intentflow-admin/api/explain").contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\": \"please decline\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.kind").value("unsupported"));
    }
}
