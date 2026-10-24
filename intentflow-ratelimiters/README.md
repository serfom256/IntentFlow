# IntentFlow rate limiters

A rate limiter caps how many new questions (DSL cache misses, the ones that call the LLM) a caller may ask per
period. The built-in limiter counts in memory, so each instance keeps its own buckets and N instances allow N times
the limit. A module here keeps the buckets in a shared store, so all instances enforce one limit together.

```kotlin
implementation("io.github.serfom256:intentflow-ratelimit-zookeeper:0.5.0")
```

| Module                    | Store                                                                               |
| ------------------------- | ----------------------------------------------------------------------------------- |
| [`intentflow-ratelimit-zookeeper`](intentflow-ratelimit-zookeeper/README.md) | Apache ZooKeeper, through Apache Curator |

`intentflow.llm.rate-limit.store` picks the store (`memory` by default, or `zookeeper`); `questions`, `period` and
`per` apply to every store:

| Setting                                   | Default     | Meaning                                                      |
| ----------------------------------------- | ----------- | ------------------------------------------------------------ |
| intentflow.llm.rate-limit.questions       | 0           | New questions per caller per period; 0 for no limit           |
| intentflow.llm.rate-limit.period          | 1m          | Length of the period; the bucket refills evenly over it       |
| intentflow.llm.rate-limit.per             | principal   | `principal` or `tenant`                                       |
| intentflow.llm.rate-limit.store           | memory      | `memory` or `zookeeper`                                       |

A caller over the limit gets a `RateLimitExceededException` (`intentflow.rate-limited`) with the time until the next
question is allowed. An application can also define its own `LlmRateLimiter` bean instead.
