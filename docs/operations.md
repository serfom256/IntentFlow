# Operations guide

How IntentFlow behaves in production, and what to size, set and watch. For what reaches the model and why a
manipulated answer cannot widen access, see the [security model](security.md).

## Where the time goes

A question that is not in the cache costs one LLM call, plus one more when the answer is rejected and repaired. The
LLM call usually takes 0.5–5 seconds; everything else (parsing, validation, SQL generation) takes about a millisecond.
The query itself costs what any indexed `SELECT` costs. A cached question skips the model entirely, so repeated
questions are answered in the time of the query.

The pipeline is blocking: the calling thread waits for the model and the database. Plan threads for the slowest
step.

| Setting                                          | Default | What to consider                                                  |
| ------------------------------------------------ | ------- | ----------------------------------------------------------------- |
| intentflow.llm.timeout                           | 60s     | Per attempt. For interactive search, 10–20 seconds fails faster    |
| intentflow.llm.max-retries, .retry-backoff       | 2, 500ms | Each retry after a 429 or 5xx is another attempt of up to `timeout` |
| intentflow.llm.max-repairs                       | 1       | Each repair is another LLM call; 0 fails a rejected answer at once |
| intentflow.sql.statement-timeout                 | 30s     | Keep it below your HTTP timeout                                    |

The longest a single question can take is about `timeout × (1 + max-retries) × (1 + max-repairs)`, plus the same
again when a fallback model is configured, plus the query. With the defaults that is about six minutes, which is
too long for a web request: lower `timeout` and `max-retries` until the product fits your gateway's timeout.

## Threads

- **Servlet applications:** each question holds a request thread while the model answers. Turn on virtual threads
  (`spring.threads.virtual.enabled=true`) or size the server's thread pool for the peak number of questions in
  flight.
- **WebFlux:** `ReactiveIntentPipeline` runs each question on Reactor's `boundedElastic` scheduler, never on the
  event loop. Its default limit is ten threads per CPU core.
- **The LLM itself:** `intentflow.llm.concurrency.max-calls` caps calls in flight per instance, so a burst of
  questions waits (up to `max-wait`) instead of overrunning the provider's quota. Set it to what your provider
  allows divided by the number of instances.

## Sizing

| Resource             | What uses it                                                                   |
| -------------------- | ------------------------------------------------------------------------------ |
| Heap                 | The DSL cache (`intentflow.cache.max-size`, 10,000 entries of a question and a short query each, a few MB), and result entities: at most `maxTake` rows per question (default 50) |
| Database connections | One per question while its query runs: the transaction starts after the model has answered, unless your own code calls the pipeline inside a transaction. Relations add a query each in the same transaction |
| LLM quota            | One call per question the cache does not answer, plus repairs. Prompt size grows with the catalog: the services, fields and descriptions the caller may query |
| Admin table          | One row per edited service                                                     |

The prompt is the main lever on token cost. Every service the caller may query is described in it, so a policy that
hides services a caller cannot use also makes their prompt shorter. The Anthropic and Bedrock providers cache the
catalog part of the prompt.

## Cache

The DSL cache maps a question to its translated query. It holds no rows and needs no invalidation when data
changes.

- It lives in each instance's memory (Caffeine), for `intentflow.cache.ttl` (10 minutes) and up to `max-size`
  entries. Each instance fills its own; a `DslCache` bean can replace it with a shared one.
- Entries are per service and per caller view, so callers with different access policies get their own.
- A catalog change, from code or the admin UI, changes the key: questions are translated again with the new
  descriptions, and old entries expire.
- Questions are normalized (whitespace collapsed) before lookup; differently worded questions are different
  entries.

`intentflow.cache` with `result=hit|miss` shows how much the cache saves. A low hit rate with many identical
questions points to a short `ttl` or a full cache.

## Several instances

| Concern            | Behaviour                                                                        |
| ------------------ | -------------------------------------------------------------------------------- |
| DSL cache          | Per instance. Each instance pays the first translation of a question once        |
| Rate limits        | Per instance with the default store, so N instances allow N times the limit. Use a shared store, Redis, JDBC or ZooKeeper (`intentflow.llm.rate-limit.store`, see [intentflow-ratelimiters](../intentflow-ratelimiters/README.md)) |
| Concurrency limit  | Per instance; divide the provider's limit by the number of instances             |
| Circuit breaker    | Per instance; each opens on its own failures                                     |
| Admin overrides    | Stored in the `intentflow_catalog_override` table; every instance re-reads it every `intentflow.admin.refresh-interval` (30s), so a change reaches all instances within that time |
| Page cursors       | A cursor carries its query, signed with `intentflow.paging.cursor-key`. Set the same key on every instance, from your secret store: then any instance continues any page without asking the model. Without it, each instance has a random key, and a cursor from another instance is accepted only if the model translates the question to the same query again |

No instance needs to know about the others, and none keeps state a restart would lose, except the cache. A restart
also changes the random cursor key, so set `cursor-key` even with one instance if pages must survive restarts.

## Database

- Connect with a user that may only `SELECT` the catalog's tables. Queries also run in a read-only transaction
  (`intentflow.sql.read-only`).
- The validator stops a question from asking for more than `maxTake` rows or skipping more than
  `intentflow.max-skip`, but it does not know which columns are indexed. A filter on an unindexed column of a large
  table runs until the statement timeout. The admin UI's "Try a question" shows the plan and suggests indexes for
  the columns people filter on.
- Paged requests (`IntentRequests.page`) continue after the last row instead of using an offset when the sort is on
  non-null columns ending in a key, so deep pages stay fast. Declare a key in the default sort (`defaultSort = "id"`)
  to get this.
- With Liquibase, include `db/intentflow/intentflow-admin.changelog.yaml` and set
  `intentflow.admin.create-table=false`.

## Monitoring

With Micrometer on the classpath, the starter records:

| Meter                 | Tags                             | Watch for                                         |
| --------------------- | -------------------------------- | ------------------------------------------------- |
| `intentflow.requests` | `service`, `outcome`             | Latency per outcome; a rise in `failed`, `rejected` or `limited` |
| `intentflow.llm`      | `kind` (generate, repair), `outcome` | Model latency and errors; many repairs mean descriptions confuse the model |
| `intentflow.llm.tokens` | `model`, `kind`, `type`        | Token spend per model                             |
| `intentflow.cache`    | `result` (hit, miss)             | Hit rate                                          |
| `intentflow.query`, `intentflow.query.rows` | `service`, `kind`, `outcome` | Slow or failing queries per service, and result sizes |

Outcomes are `answered`, `declined` (the model said the question cannot be expressed), `rejected` (its answer
failed validation), `denied` (access policy), `limited` (rate limit) and `failed`.

With Micrometer Observation (for example Micrometer Tracing), every question becomes a span with child spans named
`intentflow.stage` for each step: generate, repair, validate, build and query. LLM spans carry the token counts.

The `intentflowLlm` Actuator health indicator asks the provider for its models, which costs no tokens, and reuses
the result for `intentflow.llm.health-interval`. Keep it out of liveness and readiness probes: a provider outage
should not restart or unroute instances that can still answer cached questions. Alert on it instead.

The audit log (`intentflow.audit.enabled`) writes one line per question and per catalog change to the
`intentflow.audit` logger. Route that logger to its own appender and retention, since it contains the questions
people typed.

## When the model is unavailable

1. Calls that fail with a 429 or 5xx are retried `max-retries` times with growing backoff.
2. After `circuit-breaker.failure-threshold` failures in a row the circuit opens for `open-for`: new questions fail
   at once with `intentflow.llm-unavailable` (and a retry-after time) instead of waiting for timeouts.
3. With `intentflow.llm.fallback.model` set, questions go to the fallback model while the primary fails.
4. Cached questions keep working throughout.

Map `intentflow.llm-unavailable` and `intentflow.rate-limited` to HTTP 503 and 429 with a `Retry-After` header;
`IntentFlowErrors.message(e, locale)` gives a message for the user.

## Upgrades and catalog changes

- New `@IntentService` and `@IntentField` declarations take effect on deploy. During a rolling deploy, instances
  briefly answer with different catalogs; each answer is still valid for the catalog that produced it.
- Page cursors keep their query across deploys as long as the cursor key stays the same; one whose query the new
  catalog rejects fails with `intentflow.invalid-cursor`, and the client starts again from the first page. Rotating
  the key invalidates the signatures, so open cursors fall back to translating the question again.
- Changing the model or its version can change translations. Run your most important questions against the new model
  before switching, and watch `rejected` and `declined` outcomes after.
