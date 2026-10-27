package io.github.serfom256.intentflow.springdata.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import io.github.serfom256.intentflow.cassandra.CassandraQuery;
import io.github.serfom256.intentflow.cassandra.CassandraQueryBuilder;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.ScanLimitExceededException;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CassandraQueryBuilderIT {

    @Table("reading")
    @IntentService(value = "readings", uri = "/readings/{{sensor}}", maxTake = 100)
    public static class Reading {
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED)
        @IntentField
        String sensor;
        @PrimaryKeyColumn(name = "taken_at", ordinal = 0, type = PrimaryKeyType.CLUSTERED)
        @IntentField
        Instant takenAt;
        @IntentField
        BigDecimal value;
        @IntentField
        Boolean ok;
        @Column("note")
        @IntentField
        String note;
    }

    @Table("plain")
    @IntentService(value = "plains", uri = "/plains/{{id}}", maxTake = 100)
    public static class Plain {
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED)
        @IntentField
        Long id;
        @IntentField
        Integer score;
    }

    @Table("item")
    @IntentService(value = "items", uri = "/items/{{code}}", maxTake = 100)
    @IntentRelation(name = "owner", service = "plains", from = "owner_id", to = "id")
    public static class Item {
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED)
        @IntentField(caseSensitive = true)
        String code;
        @IntentField
        String label;
        @Column("owner_id")
        @IntentField
        Long ownerId;
    }

    private CqlSession session;
    private ServiceCatalog catalog;
    private CassandraQueryBuilder builder;

    @BeforeAll
    void createTables() {
        session = CassandraTestDatabase.session();
        session.execute("DROP TABLE IF EXISTS reading");
        session.execute("CREATE TABLE reading (sensor text, taken_at timestamp, value decimal, ok boolean, note text, "
                + "PRIMARY KEY (sensor, taken_at))");
        session.execute("DROP TABLE IF EXISTS plain");
        session.execute("CREATE TABLE plain (id bigint PRIMARY KEY, score int)");
        for (int i = 1; i <= 30; i++) {
            session.execute("INSERT INTO plain (id, score) VALUES (" + i + ", " + (i % 7) + ")");
        }
        session.execute("DROP TABLE IF EXISTS item");
        session.execute("CREATE TABLE item (code text PRIMARY KEY, label text, owner_id bigint)");
        session.execute("INSERT INTO item (code, label, owner_id) VALUES ('A1', 'Lamp', 1)");
        session.execute("INSERT INTO item (code, label, owner_id) VALUES ('a1', 'lamp', 2)");
        List<CassandraNameResolver> resolvers = List.of(new CassandraNameResolver());
        catalog = ServiceCatalogs.of(ServiceDescriptors.fromAnnotated(Reading.class, List.copyOf(resolvers)),
                ServiceDescriptors.fromAnnotated(Plain.class, List.copyOf(resolvers)),
                ServiceDescriptors.fromAnnotated(Item.class, List.copyOf(resolvers)));
        builder = new CassandraQueryBuilder(session, ZoneOffset.UTC);
    }

    @AfterAll
    void close() {
        session.close();
    }

    private CassandraQuery build(String dsl) {
        AstQuery ast = DslParsers.create().parse(DslSources.of(dsl));
        return (CassandraQuery) builder.build(AstValidators.create().validate(ast, catalog));
    }

    @Test
    void exactConditionsOnRegularColumnsArePushedDown() {
        CassandraQuery q = build(
                "readings | filter value >= 1.5 and value < 9 and ok == true " + "and taken_at > \"2026-01-01\"");

        assertThat(q.cql()).isEqualTo(
                "SELECT * FROM intentflow.reading WHERE value >= ? AND value < ? AND ok = ? " + "ALLOW FILTERING");
        assertThat(q.bindValues()).containsExactly(new BigDecimal("1.5"), new BigDecimal("9"), true);
        session.execute(SimpleStatement.newInstance(q.cql(), q.bindValues().toArray()));
    }

    @Test
    void textOrNegatedOrDisjunctiveConditionsStayInMemory() {
        CassandraQuery q = build("readings | filter sensor == \"S1\" and note ~ \"x\" and value != 3 "
                + "and (ok == true or value > 2) and not (value < 0)");

        assertThat(q.cql()).isEqualTo("SELECT * FROM intentflow.reading");
        assertThat(q.bindValues()).isEmpty();
    }

    @Test
    void singleColumnPartitionKeyEqualityIsPushedButRangeIsNot() {
        assertThat(build("plains | filter id == 7").cql())
                .isEqualTo("SELECT * FROM intentflow.plain WHERE id = ? ALLOW FILTERING");
        assertThat(build("plains | filter id > 7").cql()).isEqualTo("SELECT * FROM intentflow.plain");
    }

    @Test
    void caseSensitiveTextEqualityIsPushedDownAndMatchesExactly() {
        CassandraQuery q = build("items | filter code == \"A1\"");

        assertThat(q.cql()).isEqualTo("SELECT * FROM intentflow.item WHERE code = ? ALLOW FILTERING");
        assertThat(q.bindValues()).containsExactly("A1");
        assertThat(CassandraQueryExecutor.of(session).execute(q, Item.class)).extracting(i -> i.label)
                .containsExactly("Lamp");
    }

    @Test
    void caseInsensitiveTextStaysInMemory() {
        CassandraQuery q = build("items | filter label == \"LAMP\"");

        assertThat(q.cql()).isEqualTo("SELECT * FROM intentflow.item");
        assertThat(CassandraQueryExecutor.of(session).execute(q, Item.class)).extracting(i -> i.code)
                .containsExactlyInAnyOrder("A1", "a1");
    }

    @Test
    void relationsAreRejectedBecauseCassandraHasNoJoins() {
        assertThatThrownBy(() -> build("items | filter owner.score > 1")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cassandra has no joins");
    }

    @Test
    void conflictingRestrictionsOnOneColumnKeepOnlyTheFirst() {
        CassandraQuery q = build("plains | filter score > 1 and score >= 2 and score == 3 and score < 6");

        assertThat(q.cql()).isEqualTo("SELECT * FROM intentflow.plain WHERE score > ? AND score < ? ALLOW FILTERING");
        assertThat(q.bindValues()).containsExactly(1, 6);
    }

    @Test
    void literalsThatDoNotFitTheColumnTypeAreNotPushed() {
        assertThat(build("plains | filter score < 2.5").cql()).isEqualTo("SELECT * FROM intentflow.plain");
    }

    @Test
    void stopsReadingOnceEnoughUnsortedRowsMatch() {
        CassandraQuery q = new CassandraQuery(List.of("SELECT * FROM intentflow.plain"), List.of(), List.of(),
                row -> true, null, 0, 5);

        assertThat(new CassandraQueryExecutor(session, converter(), 5).execute(q, Plain.class)).hasSize(5);
    }

    @Test
    void sortedQueriesFailWhenTheScanLimitIsExceeded() {
        CassandraQuery q = build("plains | filter score > 0 | sort score desc | take 3");

        assertThatThrownBy(() -> new CassandraQueryExecutor(session, converter(), 10).execute(q, Plain.class))
                .isInstanceOf(ScanLimitExceededException.class).hasMessageContaining("more than 10 rows");
        assertThat(new CassandraQueryExecutor(session, converter(), 30).execute(q, Plain.class))
                .extracting(p -> p.score).containsExactly(6, 6, 6);
    }

    @Test
    void rejectsQueriesFromOtherBuilders() {
        assertThatThrownBy(() -> CassandraQueryExecutor.of(session).execute(BoundQueries.of("SELECT 1"), Plain.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CassandraQueryBuilder");
    }

    @Test
    void unknownTableFailsWithItsName() {
        CassandraQueryBuilder other = new CassandraQueryBuilder(session, ZoneOffset.UTC);
        ServiceCatalog missing = ServiceCatalogs.of(ServiceDescriptors.of(ServiceNames.of("ghosts"), null,
                TableNames.of("ghost"), catalog.find(ServiceNames.of("plains")).orElseThrow().fields(), List.of(),
                MaxTakes.of(10), UriTemplates.of("/g"), Plain.class));
        AstQuery ast = DslParsers.create().parse(DslSources.of("ghosts"));

        assertThatThrownBy(() -> other.build(AstValidators.create().validate(ast, missing)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("intentflow.ghost");
    }

    private CassandraConverter converter() {
        MappingCassandraConverter converter = new MappingCassandraConverter();
        converter.setCodecRegistry(session.getContext().getCodecRegistry());
        converter.afterPropertiesSet();
        return converter;
    }
}
