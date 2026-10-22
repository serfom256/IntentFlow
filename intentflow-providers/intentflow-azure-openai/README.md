# intentflow-azure-openai

Connects IntentFlow to an Azure OpenAI deployment through Azure's OpenAI v1 API. It reuses the `intentflow-openai`
client and signs requests with an API key or with Entra ID.

```kotlin
implementation("io.github.serfom256:intentflow-azure-openai:0.4.0")
```

```yaml
intentflow:
  llm:
    provider: azure-openai
    base-url: https://my-resource.openai.azure.com
    model: <deployment name>
    api-key: ${AZURE_OPENAI_KEY}
```

| Setting                   | Meaning                                                                        |
| ------------------------- | ------------------------------------------------------------------------------ |
| intentflow.llm.base-url   | The resource endpoint; `/openai/v1` is appended unless it is already there     |
| intentflow.llm.model      | The deployment name                                                            |
| intentflow.llm.api-key    | Sent in the `api-key` header; leave it empty to sign in with Entra ID          |

**Entra ID.** Without an API key the client takes a token for `https://cognitiveservices.azure.com/.default` from
`DefaultAzureCredential` (managed identity, workload identity, environment, Azure CLI) and renews it before it
expires. Add `com.azure:azure-identity` to the application for this; the module does not bring it.

Without the starter: `AzureOpenAiLlmClients.of(endpoint, deployment, apiKey, timeout, clock, LlmHttpOptions.DEFAULTS)`.
