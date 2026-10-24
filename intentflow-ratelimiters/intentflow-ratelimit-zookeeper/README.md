# intentflow-ratelimit-zookeeper

Keeps the rate limit buckets in Apache ZooKeeper, so every instance of the application enforces one limit per caller.
Each caller has one znode named by the SHA-256 of the caller key, so principals and tenants are not stored in clear
text. Instances update it with a versioned compare-and-set, so concurrent questions never exceed the limit.

```kotlin
implementation("io.github.serfom256:intentflow-ratelimit-zookeeper:0.5.2")
```

```yaml
intentflow:
  llm:
    rate-limit:
      questions: 20
      period: 1m
      store: zookeeper
      zookeeper:
        connect: zk1:2181,zk2:2181,zk3:2181
```

| Setting                                        | Default                | Meaning                                    |
| ---------------------------------------------- | ---------------------- | ------------------------------------------ |
| intentflow.llm.rate-limit.zookeeper.connect    | -                      | ZooKeeper connection string; not needed when the application has a `CuratorFramework` bean |
| intentflow.llm.rate-limit.zookeeper.path       | /intentflow/rate-limit | Parent znode of the buckets                |
| intentflow.llm.rate-limit.zookeeper.fail-open  | true                   | While ZooKeeper is unreachable, `true` lets questions through and logs a warning; `false` refuses them |

Without Spring, create the limiter directly and pass it to `IntentPipelines.builder().rateLimiter(...)`:

```java
LlmRateLimiter limiter = new ZooKeeperRateLimiter(curator, "/intentflow/rate-limit", 20, Duration.ofMinutes(1),
        LlmRateLimiters.byPrincipal(), true, Clock.systemUTC());
```
