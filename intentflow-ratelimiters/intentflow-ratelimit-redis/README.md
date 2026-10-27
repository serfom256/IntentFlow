# intentflow-ratelimit-redis

Keeps the rate limit buckets in Redis or Valkey, so every instance of the application enforces one limit per caller.
A Lua script refills a caller's bucket and takes a token in one atomic step, and each bucket expires after one
period without questions, when it would be full again anyway. It is tested on Redis 8 and Valkey 9, and needs only
Lua scripting, which managed services such as Amazon ElastiCache, Azure Cache for Redis and Google Memorystore
provide.

```kotlin
implementation("io.github.serfom256:intentflow-ratelimit-redis:0.9.0")
implementation("org.springframework.boot:spring-boot-starter-data-redis")
```

```yaml
spring:
  data:
    redis:
      url: redis://redis.internal:6379
intentflow:
  llm:
    rate-limit:
      questions: 20
      period: 1m
      store: redis
```

The limiter uses the application's Redis connection (`spring.data.redis.*`), including SSL, Sentinel and Cluster.

| Setting                                   | Default                | Meaning                                    |
| ----------------------------------------- | ---------------------- | ------------------------------------------ |
| intentflow.llm.rate-limit.redis.prefix    | intentflow:rate-limit: | Prefix of the bucket keys                  |
| intentflow.llm.rate-limit.redis.fail-open | true                   | While Redis is unreachable, `true` lets questions through and logs a warning; `false` refuses them |

Without Spring Boot, create the limiter from a `StringRedisTemplate` and pass it to
`IntentPipelines.builder().rateLimiter(...)`:

```java
LlmRateLimiter limiter = new RedisRateLimiter(template, "intentflow:rate-limit:", 20, Duration.ofMinutes(1),
        LlmRateLimiters.byPrincipal(), true, Clock.systemUTC());
```
