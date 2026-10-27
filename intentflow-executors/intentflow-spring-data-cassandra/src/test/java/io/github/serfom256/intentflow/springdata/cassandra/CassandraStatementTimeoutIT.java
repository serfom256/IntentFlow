package io.github.serfom256.intentflow.springdata.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.DriverTimeoutException;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import io.github.serfom256.intentflow.cassandra.CassandraQuery;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CassandraStatementTimeoutIT {

    private static final int ROWS = 5000;

    @Table("log_line")
    public static class LogLine {
        @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED)
        Long id;
        String text;
    }

    private CqlSession session;
    private MappingCassandraConverter converter;

    @BeforeAll
    void fillTable() {
        session = CassandraTestDatabase.session(DriverConfigLoader.programmaticBuilder()
                .withDuration(DefaultDriverOption.NETTY_TIMER_TICK_DURATION, Duration.ofMillis(1)).build());
        session.execute("DROP TABLE IF EXISTS log_line");
        session.execute("CREATE TABLE log_line (id bigint PRIMARY KEY, text text)");
        PreparedStatement insert = session.prepare("INSERT INTO log_line (id, text) VALUES (?, ?)");
        String text = "x".repeat(200);
        for (int from = 0; from < ROWS; from += 500) {
            CompletableFuture.allOf(IntStream.range(from, from + 500)
                    .mapToObj(i -> session.executeAsync(insert.bind((long) i, text)).toCompletableFuture())
                    .toArray(CompletableFuture[]::new)).join();
        }
        converter = new MappingCassandraConverter();
        converter.setCodecRegistry(session.getContext().getCodecRegistry());
        converter.afterPropertiesSet();
    }

    @AfterAll
    void close() {
        session.close();
    }

    @Test
    void theStatementTimeoutCancelsASlowQuery() {
        assertThatThrownBy(() -> executor(Duration.ofMillis(1)).execute(everyLine(), LogLine.class))
                .isInstanceOf(DriverTimeoutException.class);
    }

    @Test
    void theSameQueryFinishesWithoutTheTimeout() {
        assertThat(executor(Duration.ZERO).execute(everyLine(), LogLine.class)).hasSize(ROWS);
    }

    private CassandraQueryExecutor executor(Duration timeout) {
        return new CassandraQueryExecutor(session, converter, ROWS, timeout);
    }

    private static CassandraQuery everyLine() {
        return new CassandraQuery(List.of("SELECT * FROM intentflow.log_line"), List.of(), List.of(), row -> true, null,
                0, ROWS);
    }
}
