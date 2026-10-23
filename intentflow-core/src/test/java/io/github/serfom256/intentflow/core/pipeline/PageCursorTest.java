package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;
import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageCursorTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id", maxTake = 10)
    record Book(@IntentField long id, @IntentField String title, @IntentField String author) {
    }

    private static final byte[] SHARED_KEY = "a key shared by every instance....".getBytes(StandardCharsets.UTF_8);
    private static final String SORTED = "books | sort title | take 5";
    private static final IntentContext CLERK = IntentContexts.of("clerk", null, Set.of());

    private final AccessPolicy noTitleForClerks = new AccessPolicy() {
        @Override
        public boolean canSee(ServiceDescriptor service, FieldMapping field, IntentContext caller) {
            return !(field.logical().value().equals("title") && "clerk".equals(caller.principal()));
        }
    };

    private final class Instance {

        final AtomicInteger llmCalls = new AtomicInteger();
        final IntentPipeline pipeline;

        Instance(byte[] key, String... answers) {
            Deque<String> queue = new ArrayDeque<>(Arrays.asList(answers));
            pipeline = IntentPipelines.builder()
                    .llm((text, catalog) -> {
                        llmCalls.incrementAndGet();
                        return DslSources.of(queue.size() > 1 ? queue.poll() : queue.peek());
                    })
                    .sqlBuilder(SqlBuilders.create(new PostgresDialect()))
                    .executor(new QueryExecutor() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                            return (List<T>) LongStream.rangeClosed(1, 6)
                                    .mapToObj(id -> new Book(id, "t" + id, "a" + id)).toList();
                        }
                    })
                    .catalog(ServiceCatalogs.fromAnnotated(Book.class))
                    .accessPolicy(noTitleForClerks)
                    .cursorKey(key)
                    .build();
        }

        IntentResponse<Book> page(String question, String cursor) {
            return pipeline.resolve(IntentRequests.page(question, cursor), Book.class);
        }

        IntentResponse<Book> page(String question, String cursor, IntentContext caller) {
            return pipeline.resolve(IntentRequests.page(question, caller, cursor), Book.class);
        }
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(IntentFlowException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_CURSOR));
    }

    @Test
    void anotherInstanceWithTheSameKeyContinuesWithoutAskingTheModel() {
        Instance first = new Instance(SHARED_KEY, SORTED);
        Instance second = new Instance(SHARED_KEY, "books | sort author | take 5");

        String cursor = first.page("books by title", null).nextCursor();
        IntentResponse<Book> next = second.page("books by title", cursor);

        assertThat(next.trace().dsl()).isEqualTo(SORTED);
        assertThat(next.trace().cached()).isTrue();
        assertThat(next.trace().query()).contains("OFFSET");
        assertThat(second.llmCalls).hasValue(0);
    }

    @Test
    void theNextPageKeepsTheQueryEvenWhenTheModelWouldNowAnswerDifferently() {
        Instance instance = new Instance(SHARED_KEY, SORTED, "books | sort author | take 5");

        String cursor = instance.page("books by title", null).nextCursor();

        assertThat(instance.page("books by title", cursor).trace().dsl()).isEqualTo(SORTED);
        assertThat(instance.llmCalls).hasValue(1);
    }

    @Test
    void withoutTheKeyTheQuestionIsTranslatedAgainAndMustMatch() {
        String cursor = new Instance(SHARED_KEY, SORTED).page("books by title", null).nextCursor();
        byte[] otherKey = "a different key of another instance".getBytes(StandardCharsets.UTF_8);

        Instance same = new Instance(otherKey, SORTED);
        assertThat(same.page("books by title", cursor).items()).isNotEmpty();
        assertThat(same.llmCalls).hasValue(1);
        assertInvalid(() -> new Instance(otherKey, "books | sort author | take 5").page("books by title", cursor));
    }

    @Test
    void anEditedCursorLosesItsSignature() {
        Instance instance = new Instance(SHARED_KEY, SORTED);
        String cursor = instance.page("books by title", null).nextCursor();
        String payload = new String(Base64.getUrlDecoder().decode(cursor.substring(0, cursor.indexOf('.'))),
                StandardCharsets.UTF_8);
        String[] parts = payload.split("\\|");
        parts[2] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("books | sort author | take 5".getBytes(StandardCharsets.UTF_8));
        String edited = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.join("|", parts).getBytes(StandardCharsets.UTF_8))
                + cursor.substring(cursor.indexOf('.'));

        assertInvalid(() -> instance.page("books by title", edited));
    }

    @Test
    void aCursorBelongsToItsQuestion() {
        Instance instance = new Instance(SHARED_KEY, SORTED);
        String cursor = instance.page("books by title", null).nextCursor();

        assertInvalid(() -> instance.page("books by author", cursor));
        assertThat(instance.llmCalls).hasValue(1);
    }

    @Test
    void aCursorIsCheckedAgainstTheCurrentCallersAccess() {
        Instance instance = new Instance(SHARED_KEY, SORTED);
        String cursor = instance.page("books by title", null).nextCursor();

        assertInvalid(() -> instance.page("books by title", cursor, CLERK));
    }
}
