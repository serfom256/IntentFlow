# IntentFlow Shop

Reference Spring Boot application: a REST API for a shop domain (products, customers, orders)
with natural-language search. Persistence uses OpenMapper, schema and seed data are managed by
Liquibase, and the application runs on PostgreSQL or MySQL

## Endpoints

| Endpoint                                         | Description                                        |
| ------------------------------------------------ | -------------------------------------------------- |
| `GET/POST/PUT/DELETE /api/{products,customers,orders}` | CRUD with `page` and `size`                  |
| `GET /api/search?q=...`                          | Unscoped search; the LLM selects the service       |
| `GET /api/{products,customers,orders}/search?q=...` | Search scoped to one service                    |

`debug=true` adds the execution trace (DSL, SQL, bind parameters) to a search response. Sample requests are in
[`shop.http`](src/main/resources/shop.http)

## Run

The LLM endpoint defaults to `http://localhost:1234/v1` (LM Studio) and is configured with
`LLM_URL`, `LLM_MODEL` and `LLM_API_KEY`

**Ephemeral PostgreSQL** provisioned by Testcontainers:

```
./gradlew :intentflow-examples:bootTestRun
```

**Local databases** from the repository's `docker-compose.yml`, run from the repository root:

```
docker compose up -d --wait
./gradlew :intentflow-examples:bootRun
./gradlew :intentflow-examples:bootRun --args=--spring.profiles.active=mysql
```

Compose exposes PostgreSQL on port 15432, MySQL on 13306 and Cassandra on 19042, and persists their
data in `./data`. Host ports are configurable via `POSTGRES_PORT`, `MYSQL_PORT` and `CASSANDRA_PORT`.
`DB_URL`, `DB_USER` and `DB_PASSWORD` connect the application to another database

## Test

```
./gradlew :intentflow-examples:test       # API tests with a scripted LlmClient
./gradlew :intentflow-examples:llmTest    # accuracy evaluation against the live LLM on PostgreSQL and MySQL
```

Integration tests provision databases with Testcontainers by default. `INTENTFLOW_TEST_DB=compose`
runs them against the Compose databases instead:

```
INTENTFLOW_TEST_DB=compose ./gradlew test
```