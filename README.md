<p align="center">
  <img src="icons/intentflow-card.png" alt="IntentFlow — Natural language to SQL for Spring Boot" width="760">
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.serfom256/intentflow-spring-boot-starter"><img alt="Maven Central" src="https://img.shields.io/maven-central/v/io.github.serfom256/intentflow-spring-boot-starter?style=flat-square&color=6366F1"></a>
  <a href="LICENSE"><img alt="License: MIT" src="https://img.shields.io/badge/license-MIT-6366F1?style=flat-square"></a>
  <img alt="Java 21+" src="https://img.shields.io/badge/Java-21%2B-6366F1?style=flat-square">
  <img alt="Spring Boot 4.x" src="https://img.shields.io/badge/Spring%20Boot-4.x-6366F1?style=flat-square">
</p>

**IntentFlow** translates natural-language queries into parameterized SQL and maps the result onto
your own entity classes. The query *"apple laptops under 2000, best rated first"* returns a `List<Product>`

The LLM never generates SQL. It emits a constrained query DSL, which IntentFlow parses, validates
against the declared catalog and compiles into SQL with bound parameters. Rows are mapped by the
persistence layer the application already uses: JDBC, Spring Data JDBC, OpenMapper, JPA or Spring Data Cassandra

## Contents

- [How it works](#how-it-works)
- [Quick start](#quick-start)
- [Entity declaration](#entity-declaration)
- [Usage](#usage)
- [Query DSL](#query-dsl)
- [Relations](#relations)
- [Aggregations](#aggregations)
- [Executors](#executors)
- [SQL dialects](#sql-dialects)
- [Cassandra](#cassandra)
- [Exceptions](#exceptions)
- [IntentFlow configuration](#intentflow-configuration)
- [Example application](#example-application)

---

## How it works

```
"apple laptops under 2000"
  → LLM:       products | filter brand == "apple" and category == "laptop" and price < 2000
  → validate:  declared fields only, type checks, allowed values
  → compile:   SELECT * FROM "product" WHERE LOWER("brand") = LOWER(?) AND ... LIMIT 50
  → execute:   List<Product>, mapped by the configured QueryExecutor
```

The DSL produced for a query is cached, so repeated queries skip the LLM call

## Quick start

Requirements: Java 21+, Spring Boot 4.x, an OpenAI-compatible chat completions endpoint

### 1. Add the dependencies

The starter provides auto-configuration only. Add exactly one executor (maps result rows onto
entities) and one dialect (generates engine-specific SQL):

```kotlin
dependencies {
    implementation("io.github.serfom256:intentflow-spring-boot-starter:0.1.0")
    implementation("io.github.serfom256:intentflow-jdbc:0.1.0")
    implementation("io.github.serfom256:intentflow-postgresql:0.1.0")
}
```

```xml
<dependency>
    <groupId>io.github.serfom256</groupId>
    <artifactId>intentflow-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.serfom256</groupId>
    <artifactId>intentflow-jdbc</artifactId>
    <version>0.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.serfom256</groupId>
    <artifactId>intentflow-postgresql</artifactId>
    <version>0.1.0</version>
</dependency>
```

Available modules are listed in [Executors](#executors) and [SQL dialects](#sql-dialects)

### 2. Configure the LLM endpoint

Any OpenAI-compatible chat completions API is supported (LM Studio, Ollama, OpenAI):

```yaml
intentflow:
  llm:
    base-url: http://localhost:1234/v1
    model: qwen/qwen3.8-27b
```

Queries run on the application's own `DataSource` or `CqlSession`; IntentFlow opens no connections of its own

### 3. Declare a queryable entity

```java
@IntentService(value = "products", uri = "/products/{{id}}", defaultSort = "id")
public record Product(
        @IntentField long id,
        @IntentField String name,
        @IntentField BigDecimal price,
        @IntentField Category category) {
}
```

Classes annotated with `@IntentService` are discovered by classpath scanning of the application's
auto-configuration packages (override with `intentflow.catalog.packages`)

### 4. Inject the pipeline

```java
@Service
@RequiredArgsConstructor
public class ProductSearch {

    private final IntentPipeline pipeline;

    public List<Product> find(String query) {
        return pipeline.resolve(IntentRequests.of(query), Product.class).items();
    }
}
```

### Core abstractions

| Type                             | Responsibility                                                                   |
| -------------------------------- | -------------------------------------------------------------------------------- |
| `IntentPipeline`                 | Entry point: natural-language request in, `IntentResponse` out                   |
| `IntentRequests.of(String)`      | Creates an `IntentRequest` from raw user input                                   |
| `IntentResponse<T>`              | Mapped entities or aggregate rows, a navigation URI and an execution trace       |
| `@IntentService`, `@IntentField` | Declare the catalog: services, queryable fields, types, allowed values           |
| `@IntentRelation`                | Exposes another service's fields to filters                                      |
| `QueryExecutor`                  | Executes the compiled query and maps rows; provided by an executor module        |
| `SqlDialect`                     | Renders engine-specific SQL fragments; provided by a dialect module              |
| `LlmClient`                      | Translates a request into the DSL; replaceable to integrate another API          |

All auto-configured beans are `@ConditionalOnMissingBean`: a user-defined bean of the same type takes precedence

## Entity declaration

`@IntentService` registers a class as a queryable service. `@IntentField` exposes a field to the LLM
for filtering and sorting; fields without it are excluded from the prompt and rejected by validation,
but are still mapped by the executor. IntentFlow annotations combine with the persistence mapper's
own, for example OpenMapper:

```java
@Model(primaryKey = "id")
@IntentService(
        value = "products",
        table = "product",
        uri = "/products/{{id}}",
        defaultSort = "id",
        description = "Items sold in the shop")
public class Product {

    @Field
    @IntentField
    private Long id;

    @Field
    @IntentField(description = "USD")
    private BigDecimal price;

    @Field
    @IntentField
    private Category category;

    @Field(name = "created_at")
    @IntentField
    private OffsetDateTime createdAt;

    @Field(name = "supplier_cost")
    private BigDecimal supplierCost;
}
```

- **Field types** are inferred from the Java type: text, number, boolean, timestamp. Enum constants
  become the field's allowed values. Types are included in the prompt and enforced during validation
- **Column names** are resolved from the mapper's annotations (`@Field`, `@Column`) and default to
  the snake_case form of the Java field name
- **`caseSensitive = true`** disables case folding for `==`, `!=` and `in`, so comparisons on codes
  and keys can use plain column indexes
- **`uri`** is a template for the navigation URI returned with each response, see [Usage](#usage)

The catalog can also be built programmatically with `ServiceDescriptors.of(...)` and `ServiceCatalogs.of(...)`

## Usage

`IntentPipeline` offers a scoped and an unscoped variant of `resolve`.

**Scoped resolution** targets a known entity type:

```java
IntentResponse<Product> response = pipeline.resolve(IntentRequests.of("out of stock monitors"), Product.class);
List<Product> products = response.items();
```

The prompt contains only the service mapped to `Product` and the services reachable through its
`@IntentRelation`s. A DSL query addressing any other service fails with `ServiceNotFoundException`

**Unscoped resolution** lets the LLM select the service from the whole catalog:

```java
IntentResponse<?> response = pipeline.resolve(IntentRequests.of("vip customers who joined this year"));

if (response.type() == Customer.class) {
    List<Customer> customers = response.items().stream().map(Customer.class::cast).toList();
}
```

`service()` returns the selected service name and `type()` its entity class

**Response contents**

| Method          | Contents                                                                                       |
| --------------- | ---------------------------------------------------------------------------------------------- |
| `items()`       | Mapped entities in query order; empty when nothing matched or the query is an aggregation      |
| `aggregation()` | Result rows of an aggregate query, `null` otherwise, see [Aggregations](#aggregations)         |
| `uri()`         | The service's `uri` template resolved against the first item (`/products/12`), or the collection path (`/products`) when there are no items |
| `trace()`       | Generated DSL (`dsl()`), cache hit flag (`cached()`), compiled statement (`query()`) and bind parameters (`parameters()`) |

**Caching.** Only the request → DSL translation is cached, keyed by the normalized request and the
scope (`intentflow.cache.*`). Every call executes the query, so results always reflect current data

**Error handling.** All failures are unchecked exceptions, see [Exceptions](#exceptions). Typically
`UnsupportedRequestException`, `ValidationException`, `DslParseException` and `ServiceNotFoundException`
map to HTTP 422, and `LlmException` to HTTP 503

## Query DSL

Each DSL query addresses exactly one service:

```
products | filter category in ("laptop", "phone") and price < 1000 | sort rating desc | take 5
customers | filter vip == true and not (created_at < "2026-01-01")
orders | filter created_at >= "2026-07-01" | sort created_at desc
```

| Stage    | Syntax                                                                            |
| -------- | --------------------------------------------------------------------------------- |
| `filter` | `==` `!=` `<` `<=` `>` `>=`, `~` (contains), `in (...)`, `== null`, `and/or/not`  |
| `sort`   | `sort field [asc\|desc], ...`                                                     |
| `skip`   | `skip 20`                                                                         |
| `take`   | `take 10`, bounded by `maxTake`                                                   |
| `group`  | `group status, category`, followed by `count`, `sum f`, `avg f`, `min f`, `max f` |

Semantics:

- Text comparisons are case-insensitive unless the field is declared `caseSensitive`
- NULLs sort last in both directions
- The service's `defaultSort` is appended to every sort as a tie-breaker, which keeps pagination
  deterministic; it should end with a unique field
- When a request cannot be expressed with the declared catalog, the LLM returns
  `unsupported "reason"`, raised as `UnsupportedRequestException`

## Relations

```java
@IntentService(value = "orders", uri = "/orders/{{id}}", defaultSort = "id")
@IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
public record Order(
        @IntentField long id,
        @IntentField long customerId,
        @IntentField String status) {
}
```

Filters may reference the target service's fields as `<relation>.<field>`; the result type is unchanged:

```
orders | filter customer.vip == true and status == "delivered"
customers | filter orders.total > 100 and orders.status == "PAID"
customers | filter not (orders.status == "CANCELLED")
```

Each relation compiles to a correlated `EXISTS` subquery, so the result is never multiplied by the
join. Conditions on the same relation within one conjunction are evaluated against the same related
row, and a negated relation condition compiles to `NOT EXISTS`. Relations are supported in filters
only, and not on Cassandra

## Aggregations

```
orders | group status | count
orders | filter customer.vip == true | sum total
products | group category | avg price | sort avg desc | take 3
```

```java
Aggregation aggregation = pipeline.resolve(IntentRequests.of("orders per status"), Order.class).aggregation();

for (AggregateRow row : aggregation.rows()) {
    Object status = row.group().getFirst();
    Object count = row.value();
}
```

Each row contains the group values in `groupBy()` order and the aggregated value; numeric values
are returned as `BigDecimal`. Grouped rows are ordered by the aggregated value descending, then by
the group fields, unless the query specifies a sort

## Executors

An executor runs the compiled query and maps result rows onto the entity class. Executor modules
are located in `intentflow-executors/`:

| Module                             | Mapping                                           | Column names from      |
| ---------------------------------- | ------------------------------------------------- | ---------------------- |
| `intentflow-jdbc`                  | Spring `JdbcClient`: records, `@Value`, JavaBeans | snake_case field names |
| `intentflow-spring-data-jdbc`      | Spring Data JDBC entity mapping                   | `@Table`, `@Column`    |
| `intentflow-openmapper`            | OpenMapper `Procedures` (`@Model`, `@Field`)      | `@Field`               |
| `intentflow-jpa`                   | JPA native query mapped onto the `@Entity`        | `@Table`, `@Column`    |
| `intentflow-spring-data-cassandra` | Spring Data Cassandra converter                   | `@Table`, `@Column`    |

When several executors are present, the starter selects the first available in the order OpenMapper,
JPA, Spring Data JDBC, Cassandra, JDBC. `intentflow.executor` selects one explicitly; a user-defined
`QueryExecutor` bean replaces auto-configuration

## SQL dialects

Dialect modules are located in `intentflow-dialects/` and are discovered via `ServiceLoader`:

| Module                  | Databases         | Notes                                                      |
| ----------------------- | ----------------- | ---------------------------------------------------------- |
| `intentflow-postgresql` | PostgreSQL, H2    | `ILIKE` for contains                                       |
| `intentflow-mysql`      | MySQL 8+, MariaDB | `LOWER() LIKE`; text equality follows the column collation |
| `intentflow-cassandra`  | Cassandra 5       | CQL query builder, see [Cassandra](#cassandra)             |

The starter resolves the dialect from the JDBC URL, falling back to the database product name from
connection metadata. `intentflow.dialect` or a user-defined `SqlDialect` bean overrides detection.
Outside Spring: `SqlBuilders.create(new MySqlDialect())`

## Cassandra

CQL lacks most DSL operators (`or`, `not`, `!=`, contains, case-insensitive comparison, sorting and
offsets on arbitrary columns). The `intentflow-cassandra` dialect therefore splits each query into
two phases, executed by `intentflow-spring-data-cassandra`:

- **CQL pushdown:** predicates CQL evaluates exactly, sent with `ALLOW FILTERING`: number, boolean
  and timestamp comparisons on regular columns, `==` on case-sensitive text columns, and `==` on a
  single-column partition key
- **In-memory evaluation:** the complete filter, sort, paging and aggregation over the fetched rows

The pushdown only narrows the scan; the in-memory phase always re-applies the full filter, so results
match the SQL semantics. A query that reads more than `intentflow.cassandra.max-scanned-rows` rows
fails with `ScanLimitExceededException`. Unsorted queries stop reading as soon as the requested page
is filled. Timestamp literals are interpreted as UTC

```java
@Table("reading")
@IntentService(value = "readings", uri = "/readings/{{sensor}}")
public class Reading {

    @PrimaryKeyColumn(type = PrimaryKeyType.PARTITIONED)
    @IntentField(caseSensitive = true)
    private String sensor;

    @Column("taken_at")
    @IntentField
    private Instant takenAt;

    @IntentField
    private BigDecimal value;
}
```

Outside Spring: `new CassandraQueryBuilder(session)` together with `CassandraQueryExecutor.of(session)`

## Exceptions

| Exception                     | Raised when                                                    |
| ----------------------------- | -------------------------------------------------------------- |
| `UnsupportedRequestException` | the LLM declined the request; `reason()` contains its reason   |
| `ValidationException`         | the DSL references an unknown field or has a type mismatch     |
| `DslParseException`           | the LLM output is not valid DSL                                |
| `ServiceNotFoundException`    | the DSL addresses an unknown or out-of-scope service           |
| `ScanLimitExceededException`  | a Cassandra query exceeded the scan limit                      |
| `LlmException`                | the LLM endpoint failed, timed out or returned no content      |

## IntentFlow configuration

| Parameter name                        | Parameter type | Default value                     | Description                                                  |
| ------------------------------------- | -------------- | --------------------------------- | ------------------------------------------------------------ |
| intentflow.enabled                    | boolean        | true                              | Enables the auto-configuration                               |
| intentflow.executor                   | string         | first available                   | `openmapper`, `jpa`, `spring-data-jdbc`, `cassandra`, `jdbc` |
| intentflow.dialect                    | string         | detected                          | `postgresql` or `mysql`                                      |
| intentflow.llm.base-url               | uri            | http://localhost:1234/v1          | Base URL of the OpenAI-compatible API                        |
| intentflow.llm.model                  | string         | -                                 | Model id; required unless an `LlmClient` bean is defined     |
| intentflow.llm.api-key                | string         | empty                             | Bearer token; empty for local servers                        |
| intentflow.llm.timeout                | duration       | 60s                               | Timeout of a single LLM request                              |
| intentflow.cache.ttl                  | duration       | 10m                               | Time to live of a cached translation                         |
| intentflow.cache.max-size             | long           | 10000                             | Maximum number of cached translations                        |
| intentflow.catalog.packages           | list<string>   | auto-configuration packages       | Packages scanned for `@IntentService` classes                |
| intentflow.cassandra.max-scanned-rows | int            | 10000                             | Maximum rows a single Cassandra query may read               |

`logging.level.io.github.serfom256.intentflow=DEBUG` logs the generated DSL, SQL and bind parameters

## Example application

[`intentflow-examples`](intentflow-examples) is a Spring Boot REST API with CRUD endpoints and
natural-language search, together with a Docker Compose setup for running it and the integration
tests against PostgreSQL, MySQL and Cassandra
