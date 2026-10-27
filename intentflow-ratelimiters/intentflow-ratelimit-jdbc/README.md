# intentflow-ratelimit-jdbc

Keeps the rate limit buckets in a table of the application's own database, so every instance enforces one limit per
caller without new infrastructure. Each caller has one row, updated with a version check, so concurrent questions
never exceed the limit; rows of callers idle for a whole period are deleted. It uses only standard SQL and is tested
on PostgreSQL, MySQL, Oracle and H2.

```kotlin
implementation("io.github.serfom256:intentflow-ratelimit-jdbc:0.9.0")
```

```yaml
intentflow:
  llm:
    rate-limit:
      questions: 20
      period: 1m
      store: jdbc
```

| Setting                                    | Default               | Meaning                                    |
| ------------------------------------------ | --------------------- | ------------------------------------------ |
| intentflow.llm.rate-limit.jdbc.table       | intentflow_rate_limit | Table of the buckets                       |
| intentflow.llm.rate-limit.jdbc.create-table | true                 | Create the table on startup if it is missing |
| intentflow.llm.rate-limit.jdbc.fail-open   | true                  | While the database is unreachable, `true` lets questions through and logs a warning; `false` refuses them |

The limiter writes to its table, while generated queries are best run by a user that may only `SELECT`. Grant that
user `SELECT`, `INSERT`, `UPDATE` and `DELETE` on the limiter's table only, or define your own `LlmRateLimiter` bean
with a separate `DataSource`. With Liquibase, include `db/intentflow/intentflow-ratelimit.changelog.yaml` and set
`create-table: false`; the table name is the changelog parameter `intentflow.rate-limit.table`.

Without Spring Boot:

```java
LlmRateLimiter limiter = new JdbcRateLimiter(dataSource, "intentflow_rate_limit", true, 20, Duration.ofMinutes(1),
        LlmRateLimiters.byPrincipal(), true, Clock.systemUTC());
```
