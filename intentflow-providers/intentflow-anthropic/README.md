# intentflow-anthropic

Connects IntentFlow to Claude through Anthropic's Messages API, using the official `com.anthropic:anthropic-java`
SDK.

```kotlin
implementation("io.github.serfom256:intentflow-anthropic:0.9.0")
```

```yaml
intentflow:
  llm:
    provider: anthropic
    model: claude-opus-5-5
    api-key: ${ANTHROPIC_API_KEY}
```

| Setting                                  | Default        | Meaning                                                         |
| ---------------------------------------- | -------------- | --------------------------------------------------------------- |
| intentflow.llm.max-tokens                | 16000          | Most tokens one answer may use, thinking included               |
| intentflow.llm.anthropic.effort          | the model's    | `low`, `medium`, `high`, `xhigh` or `max`; `low` is usually enough for translating a question |
| intentflow.llm.anthropic.refusal-fallback | true          | Let Anthropic retry a declined request on its recommended fallback model |
| intentflow.llm.base-url                  | Anthropic's API | Another endpoint, such as a gateway                            |

The catalog prompt is sent as a cached system block, so repeated questions read it from Anthropic's prompt cache;
`trace().tokens()` counts cached tokens as prompt tokens. No temperature is sent: current Claude models reject
sampling parameters. A request the model declines fails with an `LlmException` naming the refusal category.
Retries and the timeout come from `intentflow.llm.max-retries` and `intentflow.llm.timeout`.

Claude in AWS or Google Cloud goes through `intentflow-bedrock` or `intentflow-vertex` instead.
