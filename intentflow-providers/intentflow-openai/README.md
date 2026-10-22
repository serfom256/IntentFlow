# intentflow-openai

Connects IntentFlow to any API that speaks OpenAI's Chat Completions format. It uses the JDK's HTTP client and brings
no other dependency.

```kotlin
implementation("io.github.serfom256:intentflow-openai:0.4.3")
```

```yaml
intentflow:
  llm:
    base-url: https://api.openai.com/v1
    model: <model id>
    api-key: ${LLM_API_KEY}
```

The client sends `POST {base-url}/chat/completions` with temperature 0, reads the token usage the provider reports,
and checks health with `GET {base-url}/models`, which spends no tokens. Retries, the proxy, extra headers and the
key header are set with the `intentflow.llm.*` properties in the main README.

Without the starter:

```java
LlmClient llm = OpenAiLlmClients.of(URI.create("https://api.openai.com/v1"), "<model id>", apiKey,
        Duration.ofSeconds(60));
```

## Vendors

The base URLs below come from each vendor's documentation; model ids change often, so take them from the vendor's
model list. A key works only with the region that issued it.

### OpenAI

`base-url: https://api.openai.com/v1`, key from platform.openai.com.

### Mistral

`base-url: https://api.mistral.ai/v1`, e.g. `model: mistral-large-latest`. Mistral also runs in Azure, AWS and
Google Cloud, and on your own servers through vLLM.

### DeepSeek

`base-url: https://api.deepseek.com/v1`, e.g. `model: deepseek-chat`. Self-hosted DeepSeek models run on vLLM or
Ollama like any open model.

### Qwen (Alibaba Cloud Model Studio)

| Region        | base-url                                                  |
| ------------- | --------------------------------------------------------- |
| International | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1`  |
| US            | `https://dashscope-us.aliyuncs.com/compatible-mode/v1`    |
| China         | `https://dashscope.aliyuncs.com/compatible-mode/v1`       |

e.g. `model: qwen-plus`. Alibaba recommends a workspace domain,
`https://<workspace-id>.<region>.maas.aliyuncs.com/compatible-mode/v1`, for production.

### GLM (Zhipu AI)

China: `base-url: https://open.bigmodel.cn/api/paas/v4`. The international platform, Z.ai, has its own host; take it
from Z.ai's documentation.

### Kimi (Moonshot AI)

International: `base-url: https://api.moonshot.ai/v1`; China: `https://api.moonshot.cn/v1`. Keys are tied to their
region: a 401 usually means the key belongs to the other one.

### Gemini (Google AI Studio)

Google's OpenAI-compatible endpoint works with an AI Studio API key; take the base URL from Google's "OpenAI
compatibility" page. Gemini in a Google Cloud project with service-account credentials is
[`intentflow-vertex`](../intentflow-vertex/README.md).

### Self-hosted models

Llama, Qwen, Mistral, DeepSeek and other open models served by:

| Server    | base-url                    |
| --------- | --------------------------- |
| Ollama    | `http://localhost:11434/v1` |
| LM Studio | `http://localhost:1234/v1`  |
| vLLM      | `http://localhost:8000/v1`  |

Leave `api-key` empty unless the server checks one.
