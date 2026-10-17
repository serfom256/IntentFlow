# IntentFlow Shop

A complete example: a shop (products, customers, orders) with CRUD and plain-language search.

| Module                                       | Stack                                                                 |
| -------------------------------------------- | --------------------------------------------------------------------- |
| [`intentflow-shop-api`](intentflow-shop-api) | Spring Boot 4, IntentFlow with OpenMapper, Liquibase, PostgreSQL or MySQL |
| [`intentflow-shop-ui`](intentflow-shop-ui)   | React 19, TypeScript, Mantine 9, TanStack Router and Query, Vite      |

The API also includes the [admin UI](../intentflow-admin-spring-boot-starter/README.md) at
http://localhost:8080/intentflow-admin/ (user `admin`, password `admin`, or `INTENTFLOW_ADMIN_PASSWORD`).

## Run

From the repository root, with an LLM at `http://localhost:1234/v1` (LM Studio; change with `LLM_URL`, `LLM_MODEL`,
`LLM_API_KEY`):

```
intentflow-examples/run.sh [testcontainers|postgres|mysql]
```

This starts the API on port 8080 and the UI on http://localhost:5173; Ctrl+C stops both. To run them separately:

```
./gradlew :intentflow-shop-api:bootTestRun                                     # PostgreSQL in a throwaway container
docker compose up -d --wait && ./gradlew :intentflow-shop-api:bootRun          # PostgreSQL from Docker Compose
./gradlew :intentflow-shop-api:bootRun --args=--spring.profiles.active=mysql   # MySQL from Docker Compose
./gradlew :intentflow-shop-ui:dev                                              # the UI, proxying /api to port 8080
```

Gradle downloads Node.js itself. Compose exposes PostgreSQL on 15432, MySQL on 13306 and Cassandra on 19042 (change
with `POSTGRES_PORT`, `MYSQL_PORT`, `CASSANDRA_PORT`) and keeps their data in `./data`. `DB_URL`, `DB_USER` and
`DB_PASSWORD` point the API at another database.

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
./gradlew :intentflow-shop-api:test        # API tests with a scripted LLM
./gradlew :intentflow-shop-api:llmTest     # questions to the live LLM, results checked against the data
./gradlew :intentflow-shop-ui:assemble     # type check and production build of the UI
```

`llmTest` asks 32 English questions on PostgreSQL and MySQL, and 576 questions in English, Russian, Chinese and
Japanese on PostgreSQL (`MultilingualLlmEvaluationTest`). Each answer is compared with results computed from the
data; every language must reach 80%.

Integration tests start their databases with Testcontainers; `INTENTFLOW_TEST_DB=compose` uses the Compose
databases instead.
