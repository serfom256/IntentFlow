# IntentFlow LLM providers

A provider module connects IntentFlow to a language model. The model only translates a question into the IntentFlow
DSL; it never sees your data or writes SQL. Every provider gets the same prompt (`LlmPrompts` in `intentflow-core`)
and goes through the same circuit breaker, concurrency limit, fallback model, rate limit, metrics and audit log.

Add one provider next to the starter:

```kotlin
implementation("io.github.serfom256:intentflow-openai:0.5.2")
```

| Module                    | Models                                                                              |
| ------------------------- | ----------------------------------------------------------------------------------- |
| [`intentflow-openai`](intentflow-openai/README.md) | Every OpenAI-compatible API: OpenAI, Mistral, DeepSeek, Qwen, GLM, Kimi, Gemini with a key, and self-hosted models on Ollama, LM Studio or vLLM |
| [`intentflow-azure-openai`](intentflow-azure-openai/README.md) | OpenAI models in your Azure subscription, with an API key or Entra ID |
| [`intentflow-anthropic`](intentflow-anthropic/README.md) | Claude through Anthropic's API, with prompt caching of the catalog |
| [`intentflow-bedrock`](intentflow-bedrock/README.md) | Claude, Llama, Mistral, Amazon Nova and others in your AWS account, with IAM credentials |
| [`intentflow-vertex`](intentflow-vertex/README.md) | Gemini on Vertex AI in your Google Cloud project, or on the Gemini Developer API |
| [`intentflow-gigachat`](intentflow-gigachat/README.md) | Sber's GigaChat, with its OAuth token exchange and certificates |

With one provider module on the classpath it is used automatically; with several, `intentflow.llm.provider` picks one
(`openai`, `azure-openai`, `anthropic`, `bedrock`, `vertex` or `gigachat`).
`intentflow.llm.model` is required for every provider. See each module's README for its settings.

An application can also define its own `LlmClient` bean and use no provider module at all.
