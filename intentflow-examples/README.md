# IntentFlow Shop

Reference application for IntentFlow: a shop domain (products, customers, orders) with CRUD and
natural-language search, split into a Spring Boot API and a React UI

| Module                                       | Stack                                                                 |
| -------------------------------------------- | --------------------------------------------------------------------- |
| [`intentflow-shop-api`](intentflow-shop-api) | Spring Boot 4, IntentFlow with the OpenMapper executor, Liquibase, PostgreSQL or MySQL |
| [`intentflow-shop-ui`](intentflow-shop-ui)   | React 19, TypeScript, Mantine 9, TanStack Router and TanStack Query, Vite |

## API

| Endpoint                                               | Description                                  |
| ------------------------------------------------------ | -------------------------------------------- |
| `GET/POST/PUT/DELETE /api/{products,customers,orders}` | CRUD with `page` and `size`                  |
| `GET /api/search?q=...`                                | Unscoped search; the LLM selects the service |
| `GET /api/{products,customers,orders}/search?q=...`    | Search scoped to one service                 |

`debug=true` adds the execution trace (DSL, SQL, bind parameters) to a search response. Sample
requests are in [`shop.http`](intentflow-shop-api/src/main/resources/shop.http)

## UI

- **Ask:** free-text question with a scope selector, example questions and recent searches. Results
  open as an entity table or, for aggregations, a bar chart with a table; the Trace tab shows the
  DSL, SQL and bind parameters with copy buttons. Query and scope are kept in the URL
- **Command palette:** <kbd>⌘ K</kbd> or <kbd>/</kbd> asks a question or jumps to a page
- **Overview:** totals per service and the latest orders
- **Products, Customers, Orders:** paginated tables with a page size selector, client-side form
  validation plus the API's field errors, and customer details on hover in the orders table

## Data

Liquibase seeds a curated set of products, customers and orders and then generates more with a fixed
random seed, up to 1,500 products, 1,000 customers and 3,000 orders, so results exceed the 50-row page
limit and queries behave like they would on real data. On PostgreSQL, `pg_trgm` GIN indexes on
`product.name` and `customer.full_name` serve the `ILIKE '%…%'` predicates IntentFlow generates for `~`

## Run

All commands run from the repository root. The LLM endpoint defaults to `http://localhost:1234/v1`
(LM Studio) and is configured with `LLM_URL`, `LLM_MODEL` and `LLM_API_KEY`

The quickest start runs the API and the UI together; Ctrl+C stops both:

```
intentflow-examples/run.sh [testcontainers|postgres|mysql]
```

To run them separately:

**1. API** on port 8080, with one of:

```
./gradlew :intentflow-shop-api:bootTestRun                                     # ephemeral PostgreSQL via Testcontainers
docker compose up -d --wait && ./gradlew :intentflow-shop-api:bootRun          # PostgreSQL from Docker Compose
./gradlew :intentflow-shop-api:bootRun --args=--spring.profiles.active=mysql   # MySQL from Docker Compose
```

**2. UI** on http://localhost:5173, proxying `/api` to the API:

```
./gradlew :intentflow-shop-ui:dev
```

Gradle downloads a pinned Node.js into the module, so no local Node installation is needed.
`SHOP_API_URL` points the proxy at an API on another address

Compose exposes PostgreSQL on port 15432, MySQL on 13306 and Cassandra on 19042, and persists their
data in `./data`. Host ports are configurable via `POSTGRES_PORT`, `MYSQL_PORT` and `CASSANDRA_PORT`.
`DB_URL`, `DB_USER` and `DB_PASSWORD` connect the API to another database

## Build and test

```
./gradlew :intentflow-shop-ui:assemble     # type check and production bundle in intentflow-shop-ui/dist
./gradlew :intentflow-shop-api:test        # API tests with a scripted LlmClient
./gradlew :intentflow-shop-api:llmTest     # accuracy evaluation against the live LLM on PostgreSQL and MySQL
```

Integration tests provision databases with Testcontainers by default. `INTENTFLOW_TEST_DB=compose`
runs them against the Compose databases instead:

```
INTENTFLOW_TEST_DB=compose ./gradlew test
```

## Clean up

```
docker compose down -v
rm -rf data
```
