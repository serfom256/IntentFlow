# IntentFlow Shop

A complete example: a shop (products, customers, orders) with CRUD and plain-language search.

| Module                                       | Stack                                                                 |
| -------------------------------------------- | --------------------------------------------------------------------- |
| [`intentflow-shop-api`](intentflow-shop-api) | Spring Boot 4, IntentFlow with OpenMapper, JPA, Jimmer or JDBC, Liquibase, PostgreSQL or MySQL |
| `intentflow-shop-domain`                     | Domain classes and the `CrudStore` interface the API is written against |
| `intentflow-shop-{openmapper,jpa,jimmer,jdbc}` | One persistence layer each: entities, stores and their Spring configuration |
| [`intentflow-shop-ui`](intentflow-shop-ui)   | React 19, TypeScript, Mantine 9, TanStack Router and Query, Vite      |

The API also includes the [admin UI](../intentflow-admin-spring-boot-starter/README.md) at
http://localhost:8080/intentflow-admin/ (user `admin`, password `admin`, or `INTENTFLOW_ADMIN_PASSWORD`).

## Run

From the repository root, with an LLM at `http://localhost:1234/v1` (LM Studio; change with `LLM_URL`, `LLM_MODEL`,
`LLM_API_KEY`):

```
intentflow-examples/run.sh [testcontainers|postgres|mysql] [openmapper|jpa|jimmer|jdbc]
```

This starts the API on port 8080 and the UI on http://localhost:5173; Ctrl+C stops both. To run them separately:

```
./gradlew :intentflow-shop-api:bootTestRun                                     # PostgreSQL in a throwaway container
docker compose up -d --wait && ./gradlew :intentflow-shop-api:bootRun          # PostgreSQL from Docker Compose
./gradlew :intentflow-shop-api:bootRun --args=--spring.profiles.active=mysql   # MySQL from Docker Compose
./gradlew :intentflow-shop-api:bootRun -Porm=jpa                              # JPA instead of OpenMapper
./gradlew :intentflow-shop-ui:dev                                              # the UI, proxying /api to port 8080
```

Gradle downloads Node.js itself. Compose exposes PostgreSQL on 15432, MySQL on 13306 and Cassandra on 19042 (change
with `POSTGRES_PORT`, `MYSQL_PORT`, `CASSANDRA_PORT`) and keeps their data in `./data`. `DB_URL`, `DB_USER` and
`DB_PASSWORD` point the API at another database.

## Persistence layers

The same shop runs on four persistence layers; a Spring profile picks one, and the REST API, UI and tests stay the
same. Each is a Gradle module of its own. The API depends on `intentflow-shop-openmapper` only; `-Porm=jpa`,
`-Porm=jimmer` or `-Porm=jdbc` adds that module with `implementation` and turns on its profile for `bootRun` and
`bootTestRun`. CRUD always runs on OpenMapper; the profile picks the entities and executor that search uses:

| Profile                | Module                       | Entities                                                             | Search executor          |
| ---------------------- | ---------------------------- | -------------------------------------------------------------------- | ------------------------ |
| `openmapper` (default) | `intentflow-shop-openmapper` | `@Model` classes, relations with `@IntentRelation`                   | OpenMapper `Procedures`  |
| `jpa`                  | `intentflow-shop-jpa`        | `@Entity` classes, relations from `@ManyToOne` / `@OneToMany`        | JPA `EntityManager`      |
| `jimmer`               | `intentflow-shop-jimmer`     | Jimmer entity interfaces, relations from `@ManyToOne` / `@OneToMany` | `JSqlClient`             |
| `jdbc`                 | `intentflow-shop-jdbc`       | records, relations with `@IntentRelation`                            | `JdbcClient`             |

What changes per profile is small: which entities `intentflow.catalog.packages` scans, which executor
`intentflow.executor` uses, and whether JPA's auto-configuration is on (see `application.yml`). The shared code works
with the plain classes in `intentflow-shop-domain` through `CrudStore`, and each module's `EntityMapper` turns its
search results into them. Field descriptions for the LLM live in `Descriptions` there, so every profile describes the shop
identically. Combine with MySQL as `-Porm=jpa --args=--spring.profiles.active=mysql`.

`OpenMapperStore` is the one `CrudStore` implementation. It reads the entity's `@Model` primary key, its `@Field`
columns and the table and page order of its `@IntentService` once, builds the statements, and runs them through
OpenMapper's `Procedures`, binding enums the way OpenMapper's DAOs do. A new type needs a `@Model` entity and one
bean in `OpenMapperStores`.

## API

| Endpoint                                               | Description                                  |
| ------------------------------------------------------ | -------------------------------------------- |
| `GET/POST/PUT/DELETE /api/{products,customers,orders}` | CRUD with `page` and `size`                  |
| `GET /api/search?q=...`                                | Search; the LLM picks the service            |
| `GET /api/{products,customers,orders}/search?q=...`    | Search in one service                        |

`debug=true` adds the DSL, SQL and parameters to a search response. Sample requests are in
[`shop.http`](intentflow-shop-api/src/main/resources/shop.http).

## UI

- **Ask:** a question with a scope selector, example questions and recent searches. Results show as a table, or a
  chart for aggregations; the Trace tab shows the DSL and SQL
- **Command palette:** <kbd>⌘ K</kbd> or <kbd>/</kbd>
- **Overview, Products, Customers, Orders:** totals, paginated tables and validated forms

## Data

Liquibase loads a curated set of records and then generates more with a fixed seed: 1,500 products, 1,000 customers
and 3,000 orders, so answers go beyond the 50-row page like on real data. Products have an optional `discount`, so
"price after discount" needs `coalesce`.

## Example questions

| Question                                               | DSL                                                                                 |
| ------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| phones under 500 after discount                        | `products \| filter category == "PHONE" and price * (1 - coalesce(discount, 0) / 100) < 500` |
| the best rated product in each category                | `products \| top 1 per category by rating desc`                                     |
| the latest order of every vip customer                 | `orders \| filter customer.vip == true \| top 1 per customer_id by created_at desc` |
| paid orders sorted by customer name                    | `orders \| filter status == "PAID" \| sort customer.name`                           |
| customers with an order over 5000, biggest order first | `customers \| filter orders.total > 5000 \| sort orders.total desc`                  |
| revenue per month in 2026                              | `orders \| filter created_at >= "2026-01-01" \| group date_trunc("month", created_at) \| sum total` |

## Tests

```
./gradlew :intentflow-shop-api:test        # API tests with a scripted LLM, on OpenMapper
./gradlew :intentflow-shop-api:check       # the same tests on OpenMapper, JPA, Jimmer and JDBC
./gradlew :intentflow-shop-api:llmTest     # questions to the live LLM, results checked against the data
./gradlew :intentflow-shop-api:performanceTest   # plans of generated SQL against hand-written SQL
./gradlew :intentflow-shop-ui:assemble     # type check and production build of the UI
```

`llmTest` asks 32 English questions on PostgreSQL and MySQL, and 576 questions in English, Russian, Chinese and
Japanese on PostgreSQL (`MultilingualLlmEvaluationTest`). Each answer is compared with results computed from the
data; every language must reach 80%.

`performanceTest` adds 200,000 customers, 2 million orders and 100,000 products to PostgreSQL and MySQL, then runs
each case twice: the SQL IntentFlow generates for a DSL query and the SQL a developer would write for it. Both must
return the same rows. A case fails when the generated SQL scans a table the hand-written SQL reads through an index,
takes more than twice as long (plus 5 ms), or, on PostgreSQL, has a planner cost more than 1.5 times higher. The
task takes about three minutes and is not part of `build` or CI.

Integration tests start their databases with Testcontainers; `INTENTFLOW_TEST_DB=compose` uses the Compose
databases instead.
